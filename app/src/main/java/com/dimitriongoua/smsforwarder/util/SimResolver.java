package com.dimitriongoua.smsforwarder.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.model.SMS;

import java.util.ArrayList;
import java.util.List;

/**
 * Retrouve la SIM qui a reçu un SMS et ses informations (emplacement, opérateur, numéro).
 * Sans la permission READ_PHONE_STATE, seul l'identifiant d'abonnement est connu.
 */
public class SimResolver {
    private static final String TAG = SimResolver.class.getSimpleName();
    public static final int UNKNOWN = -1;
    // Extra posé par Android sur SMS_RECEIVED (constante publique à partir d'Android 11).
    private static final String EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX";
    private static final String EXTRA_SUBSCRIPTION_LEGACY = "subscription";

    private SimResolver() {
    }

    public static int subscriptionIdFrom(Intent intent) {
        int id = intent.getIntExtra(EXTRA_SUBSCRIPTION_INDEX, UNKNOWN);
        if (id == UNKNOWN) id = intent.getIntExtra(EXTRA_SUBSCRIPTION_LEGACY, UNKNOWN);
        return id;
    }

    /** Complète le SMS avec les informations de la SIM qui l'a reçu. */
    public static void fill(Context context, SMS sms, int subscriptionId) {
        sms.setSubscriptionId(subscriptionId);
        SubscriptionInfo info = find(context, subscriptionId);
        if (info == null) return;
        sms.setSimSlot(info.getSimSlotIndex());
        CharSequence carrier = info.getCarrierName();
        sms.setSimCarrier(carrier == null ? null : carrier.toString());
        sms.setSimNumber(numberOf(info));
    }

    /** SIM actives du téléphone (liste vide sans permission). */
    @SuppressLint("MissingPermission")
    public static List<SubscriptionInfo> activeSims(Context context) {
        if (!canRead(context)) return new ArrayList<>();
        try {
            List<SubscriptionInfo> list = SubscriptionManager.from(context).getActiveSubscriptionInfoList();
            return list == null ? new ArrayList<>() : list;
        } catch (SecurityException e) {
            Log.e(TAG, "Lecture des SIM refusée", e);
            return new ArrayList<>();
        }
    }

    @SuppressLint("MissingPermission")
    private static SubscriptionInfo find(Context context, int subscriptionId) {
        if (subscriptionId == UNKNOWN || !canRead(context)) return null;
        try {
            return SubscriptionManager.from(context).getActiveSubscriptionInfo(subscriptionId);
        } catch (SecurityException e) {
            Log.e(TAG, "Lecture de la SIM refusée", e);
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static String numberOf(SubscriptionInfo info) {
        String number = info.getNumber();
        return number == null || number.trim().isEmpty() ? null : number.trim();
    }

    private static boolean canRead(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }
}
