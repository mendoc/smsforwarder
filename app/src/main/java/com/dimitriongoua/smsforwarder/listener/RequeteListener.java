package com.dimitriongoua.smsforwarder.listener;

import com.android.volley.VolleyError;
import org.json.JSONObject;

public interface RequeteListener {
    public void reponse(JSONObject response);
    public void erreur(VolleyError error);
}
