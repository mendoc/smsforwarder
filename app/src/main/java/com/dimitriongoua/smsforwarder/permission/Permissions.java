package com.dimitriongoua.smsforwarder.permission;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Les 4 autorisations dont dépend le relais (écran Autorisations, alerte des Réglages) :
 * recevoir et lire les SMS, téléphone (SIM de chaque SMS), batterie sans restriction.
 */
public final class Permissions {
    public enum Kind { RECEIVE_SMS, READ_SMS, PHONE, BATTERY }

    /** Autorisations demandées par le système (la batterie passe par ses réglages). */
    public static final String[] RUNTIME = {
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.READ_PHONE_STATE,
    };

    private Permissions() {
    }

    public static String permissionOf(Kind kind) {
        switch (kind) {
            case RECEIVE_SMS:
                return Manifest.permission.RECEIVE_SMS;
            case READ_SMS:
                return Manifest.permission.READ_SMS;
            case PHONE:
                return Manifest.permission.READ_PHONE_STATE;
            default:
                return null;
        }
    }

    public static boolean granted(Context context, Kind kind) {
        if (kind == Kind.BATTERY) {
            PowerManager power = context.getSystemService(PowerManager.class);
            return power != null && power.isIgnoringBatteryOptimizations(context.getPackageName());
        }
        return ContextCompat.checkSelfPermission(context, permissionOf(kind)) == PackageManager.PERMISSION_GRANTED;
    }

    /** Autorisations manquantes, dans l'ordre de l'écran. */
    public static List<Kind> missing(Context context) {
        List<Kind> missing = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            if (!granted(context, kind)) missing.add(kind);
        }
        return missing;
    }

    /** Une autorisation système (SMS, téléphone) manque : l'écran Autorisations s'ouvre. */
    public static boolean runtimeMissing(Context context) {
        for (String permission : RUNTIME) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) return true;
        }
        return false;
    }
}
