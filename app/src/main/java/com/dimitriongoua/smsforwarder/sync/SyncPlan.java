package com.dimitriongoua.smsforwarder.sync;

/**
 * Règles de la synchronisation, sur l'horloge du téléphone (heure de réception) :
 * <ul>
 *     <li><b>point de départ</b> : la réception du premier SMS envoyé avec succès. Les SMS
 *     antérieurs ne sont jamais repris ;</li>
 *     <li><b>fenêtre lue</b> dans la boîte de réception : depuis la dernière synchronisation
 *     réussie, moins une marge (l'heure enregistrée par Android peut différer de quelques
 *     secondes de celle vue à la réception). Le journal évite les doublons dans la marge ;</li>
 *     <li><b>avancement</b> : la dernière synchronisation réussie n'avance que jusqu'au plus
 *     ancien SMS encore non synchronisé, et ne recule jamais ;</li>
 *     <li><b>abandon</b> : un envoi sans réponse depuis plus de 7 jours est marqué en échec,
 *     pour ne pas bloquer l'avancement indéfiniment.</li>
 * </ul>
 * Classe sans dépendance Android, testée par SyncPlanTest.
 */
public final class SyncPlan {
    public static final long NONE = 0;
    static final long WINDOW_MARGIN_MS = 2 * 60 * 1000;
    static final long ORIGIN_MARGIN_MS = 5 * 1000;
    public static final long RETRY_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000;

    private SyncPlan() {
    }

    /** Début de la lecture de la boîte de réception, ou {@link #NONE} sans point de départ. */
    public static long scanFrom(long origin, long lastSync) {
        if (origin <= 0) return NONE;
        long from = Math.max(lastSync, origin) - WINDOW_MARGIN_MS;
        return Math.max(from, origin - ORIGIN_MARGIN_MS);
    }

    /** SMS reçu avant le point de départ : jamais repris. */
    public static boolean beforeOrigin(long receivedAt, long origin) {
        return origin <= 0 || receivedAt < origin - ORIGIN_MARGIN_MS;
    }

    /**
     * @param oldestOpen heure de réception du plus ancien SMS encore à envoyer, {@link #NONE} s'il n'y en a pas
     * @param scanStart  heure du début de la synchronisation
     */
    public static long nextLastSync(long previous, long scanStart, long oldestOpen) {
        long candidate = oldestOpen > 0 ? Math.min(oldestOpen, scanStart) : scanStart;
        return Math.max(previous, candidate);
    }

    public static boolean expired(long receivedAt, long now) {
        return now - receivedAt > RETRY_MAX_AGE_MS;
    }
}
