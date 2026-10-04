package com.dimitriongoua.smsforwarder.sync;

import android.content.Context;
import android.util.Log;

import com.dimitriongoua.smsforwarder.filter.SmsFilter;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.DeliveryStatus;
import com.dimitriongoua.smsforwarder.journal.Fingerprint;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.journal.JournalEntry;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.Dispatcher;
import com.dimitriongoua.smsforwarder.send.Forwarder;
import com.dimitriongoua.smsforwarder.util.Settings;

import java.util.List;

/**
 * Synchronisation des SMS manqués (coupure réseau, application arrêtée) :
 * <ol>
 *     <li>renvoi des envois du journal encore en attente ou à retenter ;</li>
 *     <li>lecture de la boîte de réception, toutes SIM confondues, depuis la dernière
 *     synchronisation réussie : un SMS retenu par les filtres et absent du journal est
 *     relayé ;</li>
 *     <li>avancement de la dernière synchronisation réussie jusqu'au plus ancien SMS encore
 *     non synchronisé (voir {@link SyncPlan}).</li>
 * </ol>
 * Un envoi déjà réussi n'est jamais refait ; /sms/incoming ignore de toute façon un SMS
 * déjà enregistré. À appeler sur {@link Forwarder#EXECUTOR}. L'appelant replanifie une
 * synchronisation s'il reste des envois à faire ({@link Result#open}).
 */
public class SyncEngine {
    private static final String TAG = SyncEngine.class.getSimpleName();
    // Un SMS de même expéditeur et même corps reçu à 10 min près est considéré comme déjà relayé.
    private static final long SIMILAR_WINDOW_MS = 10 * 60 * 1000;

    /** Bilan d'une synchronisation. */
    public static final class Result {
        public final int recovered;
        public final int open;

        Result(int recovered, int open) {
            this.recovered = recovered;
            this.open = open;
        }
    }

    private final Context context;

    private SyncEngine(Context context) {
        this.context = context.getApplicationContext();
    }

    public static SyncEngine with(Context context) {
        return new SyncEngine(context);
    }

    public Result run() {
        long start = System.currentTimeMillis();
        Settings settings = Settings.with(context);
        JournalDb journal = JournalDb.get(context);
        Dispatcher dispatcher = Dispatcher.with(context);
        journal.purge(start);

        // 1. Envois du journal restés en attente.
        for (JournalEntry entry : journal.open()) {
            SMS sms = toSms(entry);
            for (Delivery delivery : entry.deliveries) {
                if (!delivery.status.isOpen()) continue;
                if (SyncPlan.expired(entry.receivedAt, start)) {
                    journal.close(entry.id, delivery.destinationKey, "Abandon : sans réponse depuis 7 jours");
                    continue;
                }
                Forwarder.Target target = dispatcher.getForwarder().targetFor(delivery.destinationKey, sms);
                if (target == null) {
                    journal.close(entry.id, delivery.destinationKey, "Destination supprimée ou désactivée");
                    continue;
                }
                dispatcher.deliver(entry.id, target, Delivery.VIA_SYNC);
            }
            if (journal.count(entry.id, DeliveryStatus.SENT) > 0) {
                settings.initSyncOrigin(entry.receivedAt);
            }
        }

        // 2. SMS de la boîte de réception absents du journal.
        int recovered = 0;
        long origin = settings.getSyncOrigin();
        long from = SyncPlan.scanFrom(origin, settings.getLastSync());
        if (from != SyncPlan.NONE) {
            SmsFilter filter = settings.getFilter();
            for (SMS sms : InboxReader.readSince(context, from)) {
                if (SyncPlan.beforeOrigin(sms.getReceivedAt(), origin)) continue;
                if (!filter.accepts(sms.getAddress(), sms.getBody())) continue;
                String fingerprint = Fingerprint.of(sms.getAddress(), sms.getTimestampMillis(), sms.getBody());
                if (journal.findId(fingerprint) >= 0) continue;
                if (journal.containsSimilar(sms.getAddress(), sms.getBody(), sms.getReceivedAt(), SIMILAR_WINDOW_MS)) continue;
                Log.d(TAG, "SMS manqué relayé : " + sms.getAddress());
                dispatcher.dispatch(sms, Delivery.VIA_SYNC);
                recovered++;
            }
        }

        // 3. Avancement de la dernière synchronisation réussie.
        List<JournalEntry> open = journal.open();
        long oldestOpen = open.isEmpty() ? SyncPlan.NONE : open.get(0).receivedAt;
        if (settings.getSyncOrigin() > 0) {
            settings.setSyncResult(SyncPlan.nextLastSync(settings.getLastSync(), start, oldestOpen), start);
        }
        int openCount = journal.countOpen();
        Log.d(TAG, "Synchronisation : " + recovered + " SMS repris, " + openCount + " envoi(s) en attente");
        return new Result(recovered, openCount);
    }

    private static SMS toSms(JournalEntry entry) {
        SMS sms = new SMS();
        sms.setAddress(entry.sender);
        sms.setBody(entry.body);
        sms.setTimestamp(String.valueOf(entry.timestamp));
        sms.setReceivedAt(entry.receivedAt);
        sms.setSubscriptionId(entry.subscriptionId);
        sms.setSimSlot(entry.simSlot);
        sms.setSimCarrier(entry.simCarrier);
        sms.setSimNumber(entry.simNumber);
        return sms;
    }
}
