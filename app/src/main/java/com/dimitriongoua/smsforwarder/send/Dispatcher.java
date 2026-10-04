package com.dimitriongoua.smsforwarder.send;

import android.content.Context;
import android.util.Log;

import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.DeliveryStatus;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.util.Settings;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Relais d'un SMS avec traçage dans le journal : le SMS y est enregistré avant le premier
 * envoi, puis chaque essai met à jour le statut de sa destination. Un envoi déjà réussi
 * n'est jamais refait. À appeler sur {@link Forwarder#EXECUTOR}.
 */
public class Dispatcher {
    private static final String TAG = Dispatcher.class.getSimpleName();

    private final Context context;
    private final Forwarder forwarder;
    private final JournalDb journal;

    private Dispatcher(Context context) {
        this.context = context.getApplicationContext();
        this.forwarder = Forwarder.with(context);
        this.journal = JournalDb.get(context);
    }

    public static Dispatcher with(Context context) {
        return new Dispatcher(context);
    }

    /**
     * Enregistre le SMS dans le journal pour toutes les destinations actives, puis l'envoie.
     *
     * @param via   {@link Delivery#VIA_RECEPTION} ou {@link Delivery#VIA_SYNC}
     * @param waits pauses entre les essais rapprochés d'une destination qui ne répond pas
     * @return true si tous les envois sont terminés (aucun à retenter)
     */
    public boolean dispatch(SMS sms, String via, long... waits) {
        List<Forwarder.Target> targets = forwarder.targets(sms);
        Map<String, String> destinations = new LinkedHashMap<>();
        for (Forwarder.Target target : targets) destinations.put(target.key, target.label);
        long smsId = journal.record(sms, Settings.with(context).getSimName(sms.getSubscriptionId()), via, destinations);
        journal.purge(System.currentTimeMillis());

        boolean done = true;
        for (Forwarder.Target target : targets) {
            DeliveryStatus status = journal.status(smsId, target.key);
            if (status != null && !status.isOpen()) continue;
            done &= deliver(smsId, target, via, waits);
        }
        return done;
    }

    /**
     * Essais vers une destination, chacun tracé dans le journal.
     *
     * @return true si l'envoi est terminé (réussi ou en échec définitif)
     */
    public boolean deliver(long smsId, Forwarder.Target target, String via, long... waits) {
        SendOutcome outcome = attempt(smsId, target, via);
        for (long wait : waits) {
            if (!outcome.shouldRetry()) break;
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            outcome = attempt(smsId, target, via);
        }
        return !outcome.shouldRetry();
    }

    private SendOutcome attempt(long smsId, Forwarder.Target target, String via) {
        SendOutcome outcome = forwarder.send(target);
        journal.recordAttempt(smsId, target.key, outcome, System.currentTimeMillis(), via);
        Log.d(TAG, "SMS " + smsId + " → " + target.label + " : " + outcome);
        return outcome;
    }

    public Forwarder getForwarder() {
        return forwarder;
    }
}
