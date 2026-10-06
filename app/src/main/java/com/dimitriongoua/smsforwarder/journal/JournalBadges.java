package com.dimitriongoua.smsforwarder.journal;

import com.dimitriongoua.smsforwarder.destination.DestinationFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Étiquettes affichées sous un SMS du journal : sa SIM, « Repris par synchro », puis l'état
 * des envois. Quand tous les envois (au moins deux) ont réussi, une seule étiquette
 * « ✓ N destinations » les résume. Chaque envoi n'affiche que le titre de sa destination
 * (coche, croix ou horloge) : l'erreur et les essais sont dans le Détail d'un SMS. Classe
 * sans dépendance Android, testée par JournalBadgesTest.
 */
public final class JournalBadges {
    /** Nature d'une étiquette : fixe sa couleur et son icône. */
    public enum Kind { SIM, SYNC, SENT, OPEN, FAILED, NONE }

    public static final class Badge {
        public final Kind kind;
        public final String text;

        Badge(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        @Override
        public String toString() {
            return kind + ":" + text;
        }
    }

    private JournalBadges() {
    }

    /** @param names titre actuel de chaque destination, par clé ({@code DestinationStore.names()}) */
    public static List<Badge> of(JournalEntry entry, Map<String, String> names) {
        List<Badge> badges = new ArrayList<>();
        badges.add(new Badge(Kind.SIM, entry.simLabel));
        if (Delivery.VIA_SYNC.equals(entry.source)) badges.add(new Badge(Kind.SYNC, "Repris par synchro"));
        if (entry.deliveries.isEmpty()) {
            badges.add(new Badge(Kind.NONE, "Aucune destination"));
            return badges;
        }
        int sent = 0;
        for (Delivery delivery : entry.deliveries) {
            if (delivery.status == DeliveryStatus.SENT) sent++;
        }
        if (sent == entry.deliveries.size() && sent > 1) {
            badges.add(new Badge(Kind.SENT, sent + " destinations"));
            return badges;
        }
        for (Delivery delivery : entry.deliveries) badges.add(badge(delivery, names));
        return badges;
    }

    /** Au moins un envoi encore à faire : la carte est mise en évidence. */
    public static boolean hasOpen(JournalEntry entry) {
        for (Delivery delivery : entry.deliveries) {
            if (delivery.status.isOpen()) return true;
        }
        return false;
    }

    private static Badge badge(Delivery delivery, Map<String, String> names) {
        String name = DestinationFormat.label(names, delivery.destinationKey, delivery.destinationLabel);
        switch (delivery.status) {
            case SENT:
                return new Badge(Kind.SENT, name);
            case FAILED:
                return new Badge(Kind.FAILED, name);
            default:
                return new Badge(Kind.OPEN, name);
        }
    }
}
