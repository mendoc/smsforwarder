package com.dimitriongoua.smsforwarder.filter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * SIM dont les SMS sont transférés, appliqué à la réception comme à la synchronisation :
 * <ul>
 *     <li>une SIM jamais réglée est active (comportement d'avant la v1.4.0) ;</li>
 *     <li>une SIM désactivée ne transfère rien ;</li>
 *     <li>une SIM réactivée ne transfère que les SMS reçus après sa réactivation : la
 *     synchronisation peut relire une fenêtre plus ancienne (marge, envoi en attente qui
 *     retient la dernière synchronisation), les SMS reçus pendant la désactivation n'y
 *     sont jamais repris ;</li>
 *     <li>un SMS dont la SIM est inconnue n'est transféré que si aucune SIM n'est
 *     désactivée, pour ne pas laisser passer ceux d'une SIM exclue.</li>
 * </ul>
 * Les envois déjà au journal (SMS accepté quand sa SIM était active) sont terminés.
 * Classe sans dépendance Android, testée par SimPolicyTest.
 */
public final class SimPolicy {
    public static final int UNKNOWN = -1;

    /** Réglage enregistré d'une SIM. */
    public static final class State {
        public final boolean enabled;
        /** Dernière activation ou désactivation (heure du téléphone). */
        public final long changedAt;

        public State(boolean enabled, long changedAt) {
            this.enabled = enabled;
            this.changedAt = changedAt;
        }
    }

    private final Map<Integer, State> states;

    public SimPolicy(Map<Integer, State> states) {
        this.states = Collections.unmodifiableMap(new HashMap<>(states));
    }

    /** SMS reçu à {@code receivedAt} par la SIM {@code subscriptionId} à transférer. */
    public boolean accepts(int subscriptionId, long receivedAt) {
        if (subscriptionId < 0) return !anyDisabled();
        State state = states.get(subscriptionId);
        if (state == null) return true;
        return state.enabled && receivedAt >= state.changedAt;
    }

    public boolean isEnabled(int subscriptionId) {
        State state = states.get(subscriptionId);
        return state == null || state.enabled;
    }

    /** Réglage de la SIM, null si elle n'a jamais été réglée. */
    public State stateOf(int subscriptionId) {
        return states.get(subscriptionId);
    }

    public boolean anyDisabled() {
        return !disabled().isEmpty();
    }

    /** SIM désactivées, y compris celles qui ne sont plus dans le téléphone. */
    public Set<Integer> disabled() {
        Set<Integer> ids = new TreeSet<>();
        for (Map.Entry<Integer, State> entry : states.entrySet()) {
            if (!entry.getValue().enabled) ids.add(entry.getKey());
        }
        return ids;
    }
}
