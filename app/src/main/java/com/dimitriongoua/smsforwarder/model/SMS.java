package com.dimitriongoua.smsforwarder.model;


import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.Serializable;

public class SMS implements Serializable {

    public String body;
    public String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String timestamp;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public JSONObject toJSONObject() {
        JSONObject params = new JSONObject();
        try {
            params.put("from", address);
            params.put("body", body);
            params.put("timestamp", timestamp);
        } catch (JSONException e) {
            Log.d("SMS", e.getMessage());
        }
        return params;
    }

    @Override
    public String toString() {
        return "SMS{" +
                "body='" + body + '\'' +
                ", address='" + address + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
