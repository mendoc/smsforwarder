package com.dimitriongoua.smsforwarder.journal;

/** Résultat de l'envoi d'un SMS vers une destination (une ligne du journal par destination). */
public final class Delivery {
    public static final String VIA_RECEPTION = "reception";
    public static final String VIA_SYNC = "synchro";

    public final String destinationKey;
    public final String destinationLabel;
    public final DeliveryStatus status;
    public final int attempts;
    public final long lastAttemptAt;
    public final String lastError;
    /** Envoi réussi à la réception ou lors d'une synchronisation (null tant qu'il n'a pas réussi). */
    public final String via;
    /** Code HTTP de la dernière réponse (0 : pas de réponse ou pas encore essayé). */
    public final int httpCode;

    public Delivery(String destinationKey, String destinationLabel, DeliveryStatus status, int attempts,
                    long lastAttemptAt, String lastError, String via) {
        this(destinationKey, destinationLabel, status, attempts, lastAttemptAt, lastError, via, 0);
    }

    public Delivery(String destinationKey, String destinationLabel, DeliveryStatus status, int attempts,
                    long lastAttemptAt, String lastError, String via, int httpCode) {
        this.destinationKey = destinationKey;
        this.destinationLabel = destinationLabel;
        this.status = status;
        this.attempts = attempts;
        this.lastAttemptAt = lastAttemptAt;
        this.lastError = lastError;
        this.via = via;
        this.httpCode = httpCode;
    }
}
