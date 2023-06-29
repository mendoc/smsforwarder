package com.dimitriongoua.smsforwarder.util;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.dimitriongoua.smsforwarder.config.EndPoints;
import com.dimitriongoua.smsforwarder.listener.RequeteListener;
import com.dimitriongoua.smsforwarder.model.SMS;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class Master {
    private static final String TAG = Master.class.getSimpleName();
    private Context context;

    private Master(Context context) {
        this.context = context;
    }

    public static Master with(Context context) {
        return new Master(context);
    }

    public void forwardSMS(SMS sms) {
        String message = sms.getBody();
        message += "\n\n" + sms.getAddress() + "\n_" + getTime(sms.getTimestamp()) + "_";
        message = message.replaceAll("\n", "%0A");

        get(EndPoints.FWD_URL.replace("{MESSAGE}", message));
    }

    public void get(String url) {
        Log.d(TAG, url);
        RequestQueue queue = Volley.newRequestQueue(this.context);
        StringRequest stringRequest = new StringRequest
                (Request.Method.GET, url, response -> {
                    Log.d(TAG, response);
                }, error -> {
                    Log.e(TAG, error.toString());
                });
        queue.add(stringRequest);
    }
    
    public void post(String url, JSONObject params, final RequeteListener listener) {
        RequestQueue queue = Volley.newRequestQueue(this.context);
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.POST, url, params, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        listener.reponse(response);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        listener.erreur(error);
                    }
                });
        queue.add(jsonObjectRequest);
    }

    private static String getTime(String timeStamp) {
        SimpleDateFormat format = new SimpleDateFormat("dd-MM-yyyy à HH:mm", Locale.FRENCH);
        Date date = new Date(Long.parseLong(timeStamp));
        return format.format(date);
    }
}
