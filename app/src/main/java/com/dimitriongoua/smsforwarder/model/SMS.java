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

    // SIM qui a reçu le SMS (SimResolver) : -1 et null quand l'information est inconnue.
    public int subscriptionId = -1;
    public int simSlot = -1;
    public String simCarrier;
    public String simNumber;

    public int getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(int subscriptionId) {
        this.subscriptionId = subscriptionId;
    }

    public int getSimSlot() {
        return simSlot;
    }

    public void setSimSlot(int simSlot) {
        this.simSlot = simSlot;
    }

    public String getSimCarrier() {
        return simCarrier;
    }

    public void setSimCarrier(String simCarrier) {
        this.simCarrier = simCarrier;
    }

    public String getSimNumber() {
        return simNumber;
    }

    public void setSimNumber(String simNumber) {
        this.simNumber = simNumber;
    }

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

    /**
     * Corps attendu par POST /sms/incoming (outil « SMS » de la console Miango) :
     * { from, body, timestamp, sim: { subscription_id, slot, carrier, number, name },
     *   device: { id, name } }
     */
    public JSONObject toInboxJSONObject(String deviceId, String deviceName, String simName) {
        JSONObject params = toJSONObject();
        try {
            JSONObject sim = new JSONObject();
            sim.put("subscription_id", subscriptionId);
            if (simSlot >= 0) sim.put("slot", simSlot);
            if (simCarrier != null) sim.put("carrier", simCarrier);
            if (simNumber != null) sim.put("number", simNumber);
            if (simName != null) sim.put("name", simName);
            params.put("sim", sim);

            JSONObject device = new JSONObject();
            device.put("id", deviceId);
            device.put("name", deviceName);
            params.put("device", device);
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
                ", subscriptionId=" + subscriptionId +
                '}';
    }
}
