package com.dimitriongoua.smsforwarder.send;

/**
 * Résultat d'un essai d'envoi vers une destination.
 * <ul>
 *     <li>{@link Status#SENT} : réponse HTTP 2xx ;</li>
 *     <li>{@link Status#FAILED} : le serveur a répondu par une erreur. Pas de nouvelle
 *     tentative automatique : /smshandler, par exemple, refuse les SMS qui ne viennent pas
 *     d'AirtelMoney et peut avoir déjà mis à jour le solde avant une erreur ;</li>
 *     <li>{@link Status#RETRY} : aucune réponse (pas de réseau, délai dépassé), le SMS
 *     sera renvoyé.</li>
 * </ul>
 * Classe sans dépendance Android, testée par SendOutcomeTest.
 */
public final class SendOutcome {

    public enum Status { SENT, FAILED, RETRY }

    private final Status status;
    private final int httpCode;
    private final String error;

    private SendOutcome(Status status, int httpCode, String error) {
        this.status = status;
        this.httpCode = httpCode;
        this.error = error;
    }

    /** Le serveur a répondu avec ce code HTTP. */
    public static SendOutcome http(int code) {
        if (code >= 200 && code < 300) return new SendOutcome(Status.SENT, code, null);
        return new SendOutcome(Status.FAILED, code, "HTTP " + code);
    }

    /** Aucune réponse du serveur : réseau absent, nom de domaine introuvable, délai dépassé. */
    public static SendOutcome noResponse(String reason) {
        return new SendOutcome(Status.RETRY, 0, reason == null || reason.isEmpty() ? "Pas de réponse" : reason);
    }

    /** Envoi impossible à construire (destination invalide…) : inutile de réessayer. */
    public static SendOutcome invalid(String reason) {
        return new SendOutcome(Status.FAILED, 0, reason);
    }

    public Status getStatus() {
        return status;
    }

    public int getHttpCode() {
        return httpCode;
    }

    /** Code ou message d'erreur, null si l'envoi a réussi. Ne contient jamais de secret. */
    public String getError() {
        return error;
    }

    public boolean isSent() {
        return status == Status.SENT;
    }

    public boolean shouldRetry() {
        return status == Status.RETRY;
    }

    @Override
    public String toString() {
        return status + (error == null ? "" : " (" + error + ")");
    }
}
