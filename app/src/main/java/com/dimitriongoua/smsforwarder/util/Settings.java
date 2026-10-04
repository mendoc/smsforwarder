package com.dimitriongoua.smsforwarder.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings.Secure;
import android.text.TextUtils;

import com.dimitriongoua.smsforwarder.config.Constants;
import com.dimitriongoua.smsforwarder.filter.SmsFilter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Paramètres de l'application, modifiables depuis l'écran principal et conservés sur le
 * téléphone : nom du téléphone, expéditeurs autorisés, règles de filtrage avancées et nom
 * de chaque SIM.
 *
 * Le nom d'une SIM saisi ici est envoyé à Miango comme nom par défaut ; un renommage dans
 * la console Miango reste prioritaire à l'affichage.
 */
public class Settings {
    private static final String PREFS = "smsforwarder_settings";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_DEVICE_NAME = "device_name";
    private static final String KEY_ALLOWED_SENDERS = "allowed_senders";
    private static final String KEY_FILTER_RULES = "filter_rules";
    private static final String KEY_SIM_NAME_PREFIX = "sim_name_";
    private static final String KEY_SYNC_ORIGIN = "sync_origin";
    private static final String KEY_LAST_SYNC = "sync_last_success";
    private static final String KEY_LAST_CHECK = "sync_last_check";

    private final Context context;
    private final SharedPreferences prefs;

    private Settings(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static Settings with(Context context) {
        return new Settings(context);
    }

    /**
     * Identifiant du téléphone : il distingue ses SIM côté Miango. Dérivé d'ANDROID_ID
     * ({@link DeviceIdentity}), il reste le même après une désinstallation puis une
     * réinstallation de l'application signée avec la même clé. Sans ANDROID_ID
     * utilisable, un identifiant aléatoire est généré une fois et conservé.
     */
    public synchronized String getDeviceId() {
        String id = DeviceIdentity.fromAndroidId(
                Secure.getString(context.getContentResolver(), Secure.ANDROID_ID), context.getPackageName());
        if (id != null) return id;
        id = prefs.getString(KEY_DEVICE_ID, null);
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

    /**
     * Règles avancées, une regex par ligne (syntaxe dans FilterRule). Liste de départ :
     * {@link SmsFilter#DEFAULT_RULES}, qui remplace la règle « paypal » de la v1.2.0.
     */
    public List<String> getFilterRules() {
        String raw = prefs.getString(KEY_FILTER_RULES, SmsFilter.DEFAULT_RULES);
        List<String> rules = new ArrayList<>();
        for (String rule : raw.split("\\n")) {
            if (!rule.trim().isEmpty()) rules.add(rule.trim());
        }
        return rules;
    }

    /** Enregistre des règles déjà validées par {@link SmsFilter#validate(List)}. */
    public void setFilterRules(List<String> rules) {
        List<String> clean = new ArrayList<>();
        for (String rule : rules) {
            if (rule != null && !rule.trim().isEmpty()) clean.add(rule.trim());
        }
        prefs.edit().putString(KEY_FILTER_RULES, TextUtils.join("\n", clean)).apply();
    }

    /** Filtre appliqué à chaque SMS : expéditeurs autorisés et règles avancées. */
    public SmsFilter getFilter() {
        return SmsFilter.fromSettings(getAllowedSenders(), getFilterRules());
    }

    /** Nom donné à la SIM dans l'application (null si aucun). */
    public String getSimName(int subscriptionId) {
        String name = prefs.getString(KEY_SIM_NAME_PREFIX + subscriptionId, null);
        return name == null || name.trim().isEmpty() ? null : name.trim();
    }

    public void setSimName(int subscriptionId, String name) {
        prefs.edit().putString(KEY_SIM_NAME_PREFIX + subscriptionId, name == null ? "" : name.trim()).apply();
    }

    /** Réception du premier SMS envoyé avec succès (0 tant qu'il n'y en a pas). */
    public long getSyncOrigin() {
        return prefs.getLong(KEY_SYNC_ORIGIN, 0);
    }

    /** Fixe le point de départ de la synchronisation, une seule fois. */
    public synchronized void initSyncOrigin(long receivedAt) {
        if (getSyncOrigin() > 0 || receivedAt <= 0) return;
        prefs.edit().putLong(KEY_SYNC_ORIGIN, receivedAt).putLong(KEY_LAST_SYNC, receivedAt).apply();
    }

    /** Horodatage de la dernière synchronisation réussie (0 si aucune). */
    public long getLastSync() {
        return prefs.getLong(KEY_LAST_SYNC, 0);
    }

    /** Dernière vérification, réussie ou non (0 si aucune). */
    public long getLastCheck() {
        return prefs.getLong(KEY_LAST_CHECK, 0);
    }

    public void setSyncResult(long lastSync, long checkedAt) {
        prefs.edit().putLong(KEY_LAST_SYNC, lastSync).putLong(KEY_LAST_CHECK, checkedAt).apply();
    }
}
