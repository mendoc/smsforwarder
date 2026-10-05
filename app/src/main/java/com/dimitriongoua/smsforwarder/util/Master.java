package com.dimitriongoua.smsforwarder.util;

import android.content.Context;
import android.util.Log;

import com.dimitriongoua.smsforwarder.model.SMS;

/** Décision de relais d'un SMS reçu. Les envois sont faits par send.Forwarder. */
public class Master {
    private static final String TAG = Master.class.getSimpleName();

    private Master() {
    }

    /** SMS à relayer selon les expéditeurs autorisés, les règles avancées et sa SIM. */
    public static boolean isAllowed(Context context, SMS sms) {
        Settings settings = Settings.with(context);
        if (!settings.getFilter().accepts(sms.getAddress(), sms.getBody())) return false;
        if (!settings.getSimPolicy().accepts(sms.getSubscriptionId(), sms.getReceivedAt())) {
            Log.d(TAG, "SMS non transféré : SIM " + sms.getSubscriptionId() + " désactivée ou inconnue");
            return false;
        }
        return true;
    }
}
