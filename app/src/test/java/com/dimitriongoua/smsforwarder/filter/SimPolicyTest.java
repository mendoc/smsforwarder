package com.dimitriongoua.smsforwarder.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.dimitriongoua.smsforwarder.sync.SyncPlan;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class SimPolicyTest {
    private static final long MIN = 60 * 1000;
    private static final long DAY = 24 * 60 * MIN;
    private static final long T0 = 1_000_000_000_000L;
    private static final int SIM_A = 3;
    private static final int SIM_B = 4;

    private static SimPolicy policy(Object... entries) {
        Map<Integer, SimPolicy.State> states = new HashMap<>();
        for (int i = 0; i < entries.length; i += 3) {
            states.put((Integer) entries[i], new SimPolicy.State((Boolean) entries[i + 1], (Long) entries[i + 2]));
        }
        return new SimPolicy(states);
    }

    @Test
    public void uneSimJamaisRegleeEstActive() {
        SimPolicy none = new SimPolicy(Collections.emptyMap());
        assertTrue(none.accepts(SIM_A, T0));
        assertTrue(none.isEnabled(SIM_A));
        assertTrue(none.accepts(SimPolicy.UNKNOWN, T0));
    }

    @Test
    public void uneSimDesactiveeNeTransfereRien() {
        SimPolicy p = policy(SIM_B, false, T0);
        assertFalse(p.accepts(SIM_B, T0 - MIN));
        assertFalse(p.accepts(SIM_B, T0 + DAY));
        assertTrue(p.accepts(SIM_A, T0 + DAY));
        assertFalse(p.isEnabled(SIM_B));
    }

    @Test
    public void uneSimReactiveeNeTransfereQueLesSmsRecusApres() {
        SimPolicy p = policy(SIM_B, true, T0);
        assertFalse(p.accepts(SIM_B, T0 - 1));
        assertTrue(p.accepts(SIM_B, T0));
        assertTrue(p.accepts(SIM_B, T0 + MIN));
    }

    @Test
    public void simInconnueTransfereeSeulementSiAucuneSimNestDesactivee() {
        assertTrue(policy(SIM_A, true, T0).accepts(SimPolicy.UNKNOWN, T0 + MIN));
        assertFalse(policy(SIM_B, false, T0).accepts(SimPolicy.UNKNOWN, T0 + MIN));
    }

    @Test
    public void lesSimDesactiveesSontListees() {
        SimPolicy p = policy(SIM_A, true, T0, SIM_B, false, T0, 9, false, T0);
        assertEquals("[4, 9]", p.disabled().toString());
        assertTrue(p.anyDisabled());
        assertFalse(policy(SIM_A, true, T0).anyDisabled());
    }

    /**
     * Un envoi en attente depuis 3 jours retient la dernière synchronisation : la boîte de
     * réception est relue sur 3 jours. La SIM B, désactivée il y a 2 jours puis réactivée
     * à l'instant, ne doit renvoyer aucun SMS reçu pendant sa désactivation.
     */
    @Test
    public void reactiverUneSimNeRenvoiePasLesSmsDeLaDesactivation() {
        long origin = T0;
        long now = T0 + 10 * DAY;
        long lastSync = now - 3 * DAY;
        long from = SyncPlan.scanFrom(origin, lastSync);
        long disabledAt = now - 2 * DAY;
        long smsDuringDisable = now - DAY;
        long smsJustBefore = now - MIN;
        assertTrue("SMS dans la fenêtre relue", smsDuringDisable >= from && smsJustBefore >= from);

        SimPolicy disabled = policy(SIM_B, false, disabledAt);
        assertFalse(disabled.accepts(SIM_B, smsDuringDisable));

        SimPolicy reenabled = policy(SIM_B, true, now);
        assertFalse(reenabled.accepts(SIM_B, smsDuringDisable));
        assertFalse(reenabled.accepts(SIM_B, smsJustBefore));
        assertTrue(reenabled.accepts(SIM_B, now + 1));
        // SMS de la SIM B reçu avant la désactivation, encore dans la fenêtre : déjà au
        // journal (envoyé ou en attente) ; la synchronisation ne le reprend pas non plus.
        assertFalse(reenabled.accepts(SIM_B, disabledAt - MIN));
    }
}
