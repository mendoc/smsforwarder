package com.dimitriongoua.smsforwarder.sync;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SyncPlanTest {
    private static final long MIN = 60 * 1000;
    private static final long ORIGIN = 1_000_000_000L;

    @Test
    public void sansPremierEnvoiReussiRienNestLu() {
        assertEquals(SyncPlan.NONE, SyncPlan.scanFrom(0, 0));
        assertTrue(SyncPlan.beforeOrigin(ORIGIN, 0));
    }

    @Test
    public void laLectureCommenceALaDerniereSynchroMoinsUneMarge() {
        assertEquals(ORIGIN + 60 * MIN - SyncPlan.WINDOW_MARGIN_MS, SyncPlan.scanFrom(ORIGIN, ORIGIN + 60 * MIN));
    }

    @Test
    public void laLectureNeRemontePasAvantLePointDeDepart() {
        assertEquals(ORIGIN - SyncPlan.ORIGIN_MARGIN_MS, SyncPlan.scanFrom(ORIGIN, 0));
        assertEquals(ORIGIN - SyncPlan.ORIGIN_MARGIN_MS, SyncPlan.scanFrom(ORIGIN, ORIGIN + 10));
    }

    @Test
    public void lesSmsAnterieursAuPointDeDepartNeSontPasRepris() {
        assertTrue(SyncPlan.beforeOrigin(ORIGIN - MIN, ORIGIN));
        assertFalse(SyncPlan.beforeOrigin(ORIGIN, ORIGIN));
        assertFalse(SyncPlan.beforeOrigin(ORIGIN + MIN, ORIGIN));
    }

    @Test
    public void laSynchroAvanceJusquAuPlusAncienSmsNonSynchronise() {
        long previous = ORIGIN;
        long scanStart = ORIGIN + 120 * MIN;
        // Tout est synchronisé : jusqu'au début de la synchronisation.
        assertEquals(scanStart, SyncPlan.nextLastSync(previous, scanStart, SyncPlan.NONE));
        // Un SMS reçu à +30 min n'est pas encore parti : la synchro s'arrête juste à lui.
        assertEquals(ORIGIN + 30 * MIN, SyncPlan.nextLastSync(previous, scanStart, ORIGIN + 30 * MIN));
        // Elle ne recule jamais, même pour un vieux SMS encore à envoyer.
        assertEquals(previous, SyncPlan.nextLastSync(previous, scanStart, ORIGIN - 60 * MIN));
    }

    @Test
    public void abandonApresSeptJours() {
        assertFalse(SyncPlan.expired(ORIGIN, ORIGIN + SyncPlan.RETRY_MAX_AGE_MS));
        assertTrue(SyncPlan.expired(ORIGIN, ORIGIN + SyncPlan.RETRY_MAX_AGE_MS + 1));
    }
}
