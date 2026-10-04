package com.dimitriongoua.smsforwarder.journal;

import com.dimitriongoua.smsforwarder.send.SendOutcome;

/** Statut de l'envoi d'un SMS vers une destination, tel qu'il est enregistré dans le journal. */
public enum DeliveryStatus {
    PENDING("pending", "en attente"),
    SENT("sent", "envoyé"),
    FAILED("failed", "en échec"),
    RETRY("retry", "nouvelle tentative");

    public final String code;
    public final String label;

    DeliveryStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public static DeliveryStatus fromCode(String code) {
        for (DeliveryStatus status : values()) {
            if (status.code.equals(code)) return status;
        }
        return PENDING;
    }

    public static DeliveryStatus from(SendOutcome outcome) {
        switch (outcome.getStatus()) {
            case SENT:
                return SENT;
            case RETRY:
                return RETRY;
            default:
                return FAILED;
        }
    }

    /** Envoi encore à faire : en attente ou à retenter. */
    public boolean isOpen() {
        return this == PENDING || this == RETRY;
    }
}
