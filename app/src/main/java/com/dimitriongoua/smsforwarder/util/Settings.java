package com.dimitriongoua.smsforwarder.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;

import com.dimitriongoua.smsforwarder.config.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paramètres de l'application, modifiables depuis l'écran principal et conservés sur le
 * téléphone : nom du téléphone, expéditeurs autorisés et nom de chaque SIM.
 *
 * Le nom d'une SIM saisi ici est envoyé à Miango comme nom par défaut ; un renommage dans
 * la console Miango reste prioritaire à l'affichage.
 */
public class Settings {
    private static final String PREFS = "smsforwarder_settings";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_ALLOWED_SENDERS = "allowed_senders";
    private static final String KEY_SIM_NAME_PREFIX = "sim_name_";

    private final SharedPreferences prefs;

    private Settings(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Settings with(Context context) {
        return new Settings(context);
    }

    /** Identifiant du téléphone, généré une seule fois : il distingue ses SIM côté Miango. */
    public synchronized String getDeviceId() {
        String id = prefs.getString(KEY_DEVICE_ID, null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_DEVICE_ID, id).apply();
        }
        return id;
    }

    public String getDeviceName() {
        return prefs.getString(KEY_DEVICE_NAME, Build.MANUFACTURER + " " + Build.MODEL);
    }

    public void setDeviceName(String name) {
        prefs.edit().putString(KEY_DEVICE_NAME, name.trim()).apply();
    }

    /** Expéditeurs dont les SMS sont relayés ; liste de départ : celle de Constants. */
    public List<String> getAllowedSenders() {
        String raw = prefs.getString(KEY_ALLOWED_SENDERS, Constants.SMS_ADDRESS);
        List<String> senders = new ArrayList<>();
        for (String sender : raw.split("[|\\n]")) {
            if (!sender.trim().isEmpty()) senders.add(sender.trim());
        }
        return senders;
    }

    public void setAllowedSenders(List<String> senders) {
        List<String> clean = new ArrayList<>();
        for (String sender : senders) {
            if (sender != null && !sender.trim().isEmpty()) clean.add(sender.trim());
        }
        prefs.edit().putString(KEY_ALLOWED_SENDERS, TextUtils.join("|", clean)).apply();
    }

    public boolean isAllowedSender(String address) {
        if (address == null) return false;
        for (String allowed : getAllowedSenders()) {
            if (allowed.equalsIgnoreCase(address.trim())) return true;
        }
        return false;
    }

    /** Nom donné à la SIM dans l'application (null si aucun). */
    public String getSimName(int subscriptionId) {
        String name = prefs.getString(KEY_SIM_NAME_PREFIX + subscriptionId, null);
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    public void setSimName(int subscriptionId, String name) {
        prefs.edit().putString(KEY_SIM_NAME_PREFIX + subscriptionId, name == null ? "" : name.trim()).apply();
    }
}
