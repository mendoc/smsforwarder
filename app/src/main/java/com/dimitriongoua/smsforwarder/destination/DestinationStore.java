package com.dimitriongoua.smsforwarder.destination;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.dimitriongoua.smsforwarder.config.Constants;
import com.dimitriongoua.smsforwarder.config.EndPoints;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Destinations enregistrées sur le téléphone : liste d'URL et configuration Telegram.
 *
 * À la première installation comme après une mise à jour depuis la v1.2.0, les valeurs
 * par défaut reprennent les destinations de la v1.2.0 : /smshandler sans en-tête,
 * /sms/incoming avec X-Sms-Token, et Telegram avec le token et la conversation du build.
 */
public class DestinationStore {
    private static final String TAG = DestinationStore.class.getSimpleName();
    private static final String PREFS = "smsforwarder_settings";
    private static final String KEY_URLS = "url_destinations";
    private static final String KEY_TELEGRAM_ENABLED = "telegram_enabled";
    private static final String KEY_TELEGRAM_TOKEN = "telegram_bot_token";
    private static final String KEY_TELEGRAM_CHAT = "telegram_chat_id";

    public static final String SMS_HANDLER_ID = "smshandler";
    public static final String SMS_INCOMING_ID = "sms-incoming";
    public static final String SMS_INCOMING_HEADER = "X-Sms-Token";

    private final SharedPreferences prefs;

    private DestinationStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static DestinationStore with(Context context) {
        return new DestinationStore(context);
    }

    public static List<UrlDestination> defaultUrls() {
        List<UrlDestination> urls = new ArrayList<>();
        urls.add(new UrlDestination(SMS_HANDLER_ID, EndPoints.WEBHOOK_URL, null, null, true));
        urls.add(new UrlDestination(SMS_INCOMING_ID, EndPoints.INBOX_URL, SMS_INCOMING_HEADER, Constants.SMS_INGEST_TOKEN, true));
        return urls;
    }

    public synchronized List<UrlDestination> getUrls() {
        String raw = prefs.getString(KEY_URLS, null);
        if (raw == null) return defaultUrls();
        List<UrlDestination> urls = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                urls.add(new UrlDestination(
                        item.optString("id", null),
                        item.optString("url", ""),
                        item.isNull("header_name") ? null : item.optString("header_name", null),
                        item.isNull("header_value") ? null : item.optString("header_value", null),
                        item.optBoolean("enabled", true)));
            }
        } catch (JSONException e) {
            Log.e(TAG, "Destinations URL illisibles, valeurs par défaut utilisées");
            return defaultUrls();
        }
        return urls;
    }

    public synchronized void setUrls(List<UrlDestination> urls) {
        JSONArray array = new JSONArray();
        try {
            for (UrlDestination url : urls) {
                JSONObject item = new JSONObject();
                item.put("id", url.getId());
                item.put("url", url.getUrl());
                item.put("header_name", url.getHeaderName() == null ? JSONObject.NULL : url.getHeaderName());
                item.put("header_value", url.getHeaderValue() == null ? JSONObject.NULL : url.getHeaderValue());
                item.put("enabled", url.isEnabled());
                array.put(item);
            }
        } catch (JSONException e) {
            throw new IllegalStateException("Destinations URL impossibles à enregistrer", e);
        }
        prefs.edit().putString(KEY_URLS, array.toString()).apply();
    }

    /** Ajoute la destination, ou remplace celle qui a le même identifiant. */
    public synchronized void saveUrl(UrlDestination destination) {
        List<UrlDestination> urls = getUrls();
        boolean replaced = false;
        for (int i = 0; i < urls.size(); i++) {
            if (urls.get(i).getId().equals(destination.getId())) {
                urls.set(i, destination);
                replaced = true;
            }
        }
        if (!replaced) urls.add(destination);
        setUrls(urls);
    }

    public synchronized void deleteUrl(String id) {
        List<UrlDestination> urls = getUrls();
        List<UrlDestination> kept = new ArrayList<>();
        for (UrlDestination url : urls) {
            if (!url.getId().equals(id)) kept.add(url);
        }
        setUrls(kept);
    }

    public synchronized UrlDestination findUrl(String id) {
        for (UrlDestination url : getUrls()) {
            if (url.getId().equals(id)) return url;
        }
        return null;
    }

    public TelegramDestination getTelegram() {
        return new TelegramDestination(
                prefs.getString(KEY_TELEGRAM_TOKEN, Constants.BOT_TOKEN),
                prefs.getString(KEY_TELEGRAM_CHAT, Constants.CHAT_ID),
                prefs.getBoolean(KEY_TELEGRAM_ENABLED, true));
    }

    public void setTelegram(TelegramDestination telegram) {
        prefs.edit()
                .putString(KEY_TELEGRAM_TOKEN, telegram.getBotToken())
                .putString(KEY_TELEGRAM_CHAT, telegram.getChatId())
                .putBoolean(KEY_TELEGRAM_ENABLED, telegram.isEnabled())
                .apply();
    }

    /** Secrets connus, à masquer dans les logs et les messages d'erreur. */
    public List<String> secrets() {
        List<String> secrets = new ArrayList<>();
        secrets.add(getTelegram().getBotToken());
        for (UrlDestination url : getUrls()) {
            if (url.getHeaderValue() != null) secrets.add(url.getHeaderValue());
        }
        return secrets;
    }
}
