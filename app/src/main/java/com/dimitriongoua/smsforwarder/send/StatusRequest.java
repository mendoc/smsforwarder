package com.dimitriongoua.smsforwarder.send;

import com.android.volley.AuthFailureError;
import com.android.volley.NetworkResponse;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.toolbox.HttpHeaderParser;

import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * POST d'un corps JSON dont seule compte le code HTTP de la réponse : le corps renvoyé
 * par la destination (JSON, texte ou vide) est ignoré.
 */
class StatusRequest extends Request<Integer> {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private final byte[] body;
    private final Map<String, String> headers;
    private final Response.Listener<Integer> listener;

    StatusRequest(String url, String jsonBody, Map<String, String> headers,
                  Response.Listener<Integer> listener, Response.ErrorListener errorListener) {
        super(Method.POST, url, errorListener);
        this.body = jsonBody.getBytes(UTF8);
        this.headers = headers == null ? new HashMap<String, String>() : headers;
        this.listener = listener;
        setShouldCache(false);
    }

    @Override
    public Map<String, String> getHeaders() throws AuthFailureError {
        return headers;
    }

    @Override
    public String getBodyContentType() {
        return "application/json; charset=utf-8";
    }

    @Override
    public byte[] getBody() {
        return body;
    }

    @Override
    protected Response<Integer> parseNetworkResponse(NetworkResponse response) {
        return Response.success(response.statusCode, HttpHeaderParser.parseCacheHeaders(response));
    }

    @Override
    protected void deliverResponse(Integer statusCode) {
        listener.onResponse(statusCode);
    }
}
