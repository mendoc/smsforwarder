package com.dimitriongoua.smsforwarder.journal;

import java.util.List;

/**
 * Filtres de l'écran Journal : statut des envois, SIM et recherche dans l'expéditeur ou le
 * texte. Produit la clause SQL de {@link JournalDb#page}. Classe sans dépendance Android,
 * testée par JournalQueryTest.
 */
public final class JournalQuery {
    public enum Status {
        /** Tous les SMS. */
        ALL,
        /** Au moins un envoi en attente ou à retenter. */
        OPEN,
        /** Au moins un envoi en échec définitif. */
        FAILED
    }

    public static final JournalQuery ALL = new JournalQuery(Status.ALL, null, null);

    public final Status status;
    /** Identifiant d'abonnement de la SIM, null pour toutes. */
    public final Integer subscriptionId;
    /** Texte cherché (expéditeur ou corps), null ou vide pour aucun. */
    public final String text;

    public JournalQuery(Status status, Integer subscriptionId, String text) {
        this.status = status == null ? Status.ALL : status;
        this.subscriptionId = subscriptionId;
        this.text = text == null || text.trim().isEmpty() ? null : text.trim();
    }

    public JournalQuery withStatus(Status status) {
        return new JournalQuery(status, null, text);
    }

    public JournalQuery withSim(Integer subscriptionId) {
        return new JournalQuery(Status.ALL, subscriptionId, text);
    }

    public JournalQuery withText(String text) {
        return new JournalQuery(status, subscriptionId, text);
    }

    /** Conditions sur la table sms (sans WHERE), jointes par AND ; vide sans filtre. */
    void appendConditions(List<String> conditions, List<String> args) {
        if (status == Status.OPEN) {
            conditions.add("id IN (SELECT sms_id FROM delivery WHERE status IN (?, ?))");
            args.add(DeliveryStatus.PENDING.code);
            args.add(DeliveryStatus.RETRY.code);
        } else if (status == Status.FAILED) {
            conditions.add("id IN (SELECT sms_id FROM delivery WHERE status = ?)");
            args.add(DeliveryStatus.FAILED.code);
        }
        if (subscriptionId != null) {
            conditions.add("subscription_id = ?");
            args.add(String.valueOf(subscriptionId));
        }
        if (text != null) {
            String pattern = "%" + escapeLike(text) + "%";
            conditions.add("(sender LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')");
            args.add(pattern);
            args.add(pattern);
        }
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
