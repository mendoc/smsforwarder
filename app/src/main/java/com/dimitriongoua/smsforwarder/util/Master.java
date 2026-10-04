package com.dimitriongoua.smsforwarder.util;

import android.content.Context;

/** Décision de relais d'un SMS reçu. Les envois sont faits par send.Forwarder. */
public class Master {

    private Master() {
    }

    /** SMS à relayer selon les expéditeurs autorisés et les règles avancées. */
    public static boolean isAllowed(Context context, String address, String body) {
        return Settings.with(context).getFilter().accepts(address, body);
    }
}
