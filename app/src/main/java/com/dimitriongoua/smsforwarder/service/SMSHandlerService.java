package com.dimitriongoua.smsforwarder.service;

import static com.dimitriongoua.smsforwarder.config.Constants.KEY_SMS;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.Dispatcher;
import com.dimitriongoua.smsforwarder.send.Forwarder;

/**
 * Relaie un SMS reçu vers toutes les destinations actives, l'une après l'autre, sur le
 * fil d'envoi de {@link Forwarder}. Chaque essai est tracé dans le journal.
 */
public class SMSHandlerService extends Service {
    private static final String TAG = SMSHandlerService.class.getSimpleName();
    // Essais rapprochés quand une destination ne répond pas (pas de réseau, délai dépassé).
    private static final long[] RETRY_WAITS_MS = {5000, 20000};

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final SMS sms = intent == null ? null : (SMS) intent.getSerializableExtra(KEY_SMS);
        if (sms == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        Log.d(TAG, "Réception du message par le service");
        Forwarder.EXECUTOR.execute(() -> {
            try {
                Dispatcher.with(this).dispatch(sms, Delivery.VIA_RECEPTION, RETRY_WAITS_MS);
            } catch (RuntimeException e) {
                Log.e(TAG, "Relais du SMS impossible", e);
            } finally {
                stopSelf(startId);
            }
        });
        // Service arrêté par le système avant la fin : le SMS est de nouveau transmis.
        return START_REDELIVER_INTENT;
    }
}
