package com.dimitriongoua.smsforwarder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.dimitriongoua.smsforwarder.sync.SyncScheduler;

/** Au démarrage du téléphone : synchronisation dès que le réseau est disponible. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SyncScheduler.ensurePeriodic(context);
        SyncScheduler.scheduleRetry(context);
    }
}
