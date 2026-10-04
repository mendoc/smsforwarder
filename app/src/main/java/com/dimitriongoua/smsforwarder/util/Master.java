package com.dimitriongoua.smsforwarder.util;

import android.content.Context;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.dimitriongoua.smsforwarder.config.Constants;
import com.dimitriongoua.smsforwarder.config.EndPoints;
import com.dimitriongoua.smsforwarder.model.SMS;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Master {
    private static final String TAG = Master.class.getSimpleName();
    private Context context;

    private Master(Context context) {
        this.context = context;
    }

    public static Master with(Context context) {
        return new Master(context);
    }

    public void forwardToTelegramBot(SMS sms) {
        String message = sms.getBody();
        message += "\n\n" + sms.getAddress() + "\n" + getTime(sms.getTimestamp());

        JSONObject params = new JSONObject();
        try {
            params.put("chat_id", Constants.CHAT_ID);
            params.put("text", message);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        String url = EndPoints.BOT_URL.replace("{BOT_TOKEN}", Constants.BOT_TOKEN);

        post(url, params);
    }

    public void forwardToURL(SMS sms) {
        JSONObject params = sms.toJSONObject();
        post(EndPoints.WEBHOOK_URL, params);
    }

    /** Enregistre le SMS et sa SIM dans Miango (outil « SMS » de la console). */
    public void forwardToInbox(SMS sms) {
        Settings settings = Settings.with(context);
        JSONObject params = sms.toInboxJSONObject(
                settings.getDeviceId(),
                settings.getDeviceName(),
                settings.getSimName(sms.getSubscriptionId()));
        Log.d(TAG, "POST: " + EndPoints.INBOX_URL);
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST,
                EndPoints.INBOX_URL,
                params,
                response -> Log.d(TAG, response.toString()),
                error -> Log.e(TAG, "Envoi à Miango impossible : " + error)) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("X-Sms-Token", Constants.SMS_INGEST_TOKEN);
                return headers;
            }
        };
        // Réseau instable : nouvelles tentatives ; Miango ignore un SMS déjà enregistré.
        request.setRetryPolicy(new DefaultRetryPolicy(15000, 4, 2f));
        Volley.newRequestQueue(this.context).add(request);
    }

    public void get(String url) {
        Log.d(TAG, "GET: " + url);
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest
                (Request.Method.GET, url, response -> {
                    Log.d(TAG, response);
                }, error -> {
                    Log.e(TAG, error.toString());
                });
        queue.add(stringRequest);
    }

    public void post(String url, JSONObject params) {
        Log.d(TAG, "POST: " + url);
        Log.d(TAG, "POST params: " + params.toString());
        RequestQueue queue = Volley.newRequestQueue(this.context);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.POST,
                url,
                params,
                response -> Log.d(TAG, response.toString()),
                error -> Log.e(TAG, error.toString()));
        queue.add(jsonObjectRequest);
    }

    private static String getTime(String timeStamp) {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy à HH:mm", Locale.FRENCH);
        Date date = new Date(Long.parseLong(timeStamp));
        return format.format(date);
    }

    /** Expéditeur présent dans la liste configurée dans l'application. */
    public static boolean isAllowed(Context context, String address) {
        return Settings.with(context).isAllowedSender(address);
    }
}
