package com.dimitriongoua.smsforwarder.send;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.RequestQueue;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.RequestFuture;
import com.android.volley.toolbox.Volley;
import com.dimitriongoua.smsforwarder.config.EndPoints;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.Secrets;
import com.dimitriongoua.smsforwarder.destination.TelegramDestination;
import com.dimitriongoua.smsforwarder.destination.UrlDestination;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.util.Settings;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Envoi d'un SMS vers ses destinations actives (URL et Telegram).
 *
 * Les envois sont synchrones : à appeler hors du fil principal (voir {@link #EXECUTOR}).
 * Aucun secret (valeur d'en-tête, token du bot) n'est écrit dans les logs.
 */
public class Forwarder {
    private static final String TAG = Forwarder.class.getSimpleName();
    private static final int SOCKET_TIMEOUT_MS = 30000;
    private static final long FUTURE_TIMEOUT_MS = 45000;

    /** Un seul fil pour tous les envois : réception et synchronisation ne se chevauchent pas. */
    public static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private static RequestQueue queue;

    private final Context context;
    private final DestinationStore store;

    private Forwarder(Context context) {
        this.context = context.getApplicationContext();
        this.store = DestinationStore.with(context);
    }

    public static Forwarder with(Context context) {
        return new Forwarder(context);
    }

    private static synchronized RequestQueue queue(Context context) {
        if (queue == null) queue = Volley.newRequestQueue(context.getApplicationContext());
        return queue;
    }

    /** Envoi prêt à partir vers une destination. */
    public static final class Target {
        public final String key;
        public final String label;
        final String url;
        final String body;
        final Map<String, String> headers;

        Target(String key, String label, String url, String body, Map<String, String> headers) {
            this.key = key;
            this.label = label;
            this.url = url;
            this.body = body;
            this.headers = headers;
        }
    }

    /** Envois vers toutes les destinations actives : Telegram d'abord, puis les URL. */
    public List<Target> targets(SMS sms) {
        List<Target> targets = new ArrayList<>();
        TelegramDestination telegram = store.getTelegram();
        if (telegram.isActive()) targets.add(telegramTarget(telegram, sms));
        for (UrlDestination url : store.getUrls()) {
            if (url.isEnabled()) targets.add(urlTarget(url, sms));
        }
        return targets;
    }

    /** Envoi vers la destination de cette clé, ou null si elle a été supprimée ou désactivée. */
    public Target targetFor(String key, SMS sms) {
        if (TelegramDestination.KEY.equals(key)) {
            TelegramDestination telegram = store.getTelegram();
            return telegram.isActive() ? telegramTarget(telegram, sms) : null;
        }
        for (UrlDestination url : store.getUrls()) {
            if (url.getKey().equals(key)) return url.isEnabled() ? urlTarget(url, sms) : null;
        }
        return null;
    }

    private Target urlTarget(UrlDestination destination, SMS sms) {
        Settings settings = Settings.with(context);
        JSONObject body = sms.toInboxJSONObject(
                settings.getDeviceId(),
                settings.getDeviceName(),
                settings.getSimName(sms.getSubscriptionId()));
        Map<String, String> headers = new HashMap<>();
        if (destination.hasHeader()) headers.put(destination.getHeaderName(), destination.getHeaderValue());
        return new Target(destination.getKey(), destination.getLabel(), destination.getUrl(), body.toString(), headers);
    }

    private Target telegramTarget(TelegramDestination telegram, SMS sms) {
        String message = sms.getBody() + "\n\n" + sms.getAddress() + "\n" + formatTime(sms.getTimestamp());
        JSONObject body = new JSONObject();
        try {
            body.put("chat_id", telegram.getChatId());
            body.put("text", message);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        String url = EndPoints.BOT_URL.replace("{BOT_TOKEN}", telegram.getBotToken());
        return new Target(TelegramDestination.KEY, TelegramDestination.LABEL, url, body.toString(), null);
    }

    /** Un essai d'envoi, bloquant (au plus ~45 s). */
    public SendOutcome send(Target target) {
        if (UrlDestination.validateUrl(target.url) != null) {
            return SendOutcome.invalid(UrlDestination.validateUrl(target.url));
        }
        RequestFuture<Integer> future = RequestFuture.newFuture();
        StatusRequest request = new StatusRequest(target.url, target.body, target.headers, future, future);
        // Les nouvelles tentatives sont gérées par l'appelant, destination par destination.
        request.setRetryPolicy(new DefaultRetryPolicy(SOCKET_TIMEOUT_MS, 0, 1f));
        future.setRequest(request);
        queue(context).add(request);

        SendOutcome outcome;
        try {
            outcome = SendOutcome.http(future.get(FUTURE_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (ExecutionException e) {
            outcome = fromError(e.getCause());
        } catch (TimeoutException e) {
            request.cancel();
            outcome = SendOutcome.noResponse("Délai dépassé");
        } catch (InterruptedException e) {
            request.cancel();
            Thread.currentThread().interrupt();
            outcome = SendOutcome.noResponse("Envoi interrompu");
        }
        Log.d(TAG, target.label + " : " + outcome);
        return outcome;
    }

    /** Plusieurs essais rapprochés tant que la destination ne répond pas. */
    public SendOutcome deliver(Target target, long... waitsMs) {
        SendOutcome outcome = send(target);
        for (long wait : waitsMs) {
            if (!outcome.shouldRetry()) break;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            outcome = send(target);
        }
        return outcome;
    }

    private SendOutcome fromError(Throwable cause) {
        if (cause instanceof VolleyError) {
            VolleyError error = (VolleyError) cause;
            if (error.networkResponse != null) return SendOutcome.http(error.networkResponse.statusCode);
            String reason = error.getClass().getSimpleName();
            if (error.getMessage() != null) reason += " : " + error.getMessage();
            return SendOutcome.noResponse(Secrets.redact(reason, store.secrets()));
        }
        String reason = cause == null ? null : Secrets.redact(cause.toString(), store.secrets());
        return SendOutcome.noResponse(reason);
    }

    private static String formatTime(String timestamp) {
        try {
            SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy à HH:mm", Locale.FRENCH);
            return format.format(new Date(Long.parseLong(timestamp)));
        } catch (NumberFormatException e) {
            return "";
        }
    }
}
