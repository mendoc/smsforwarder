package com.dimitriongoua.smsforwarder.journal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Textes de l'écran Détail d'un SMS (maquette « Détail d'un SMS »). Classe sans dépendance
 * Android, testée par DetailFormatTest.
 */
public final class DetailFormat {
    private DetailFormat() {
    }

    /** « 05/10 · 14:26:02 » */
    public static String received(long millis, TimeZone zone) {
        return format("dd/MM", millis, zone) + " · " + format("HH:mm:ss", millis, zone);
    }

    public static String time(long millis, TimeZone zone) {
        return millis <= 0 ? "" : format("HH:mm:ss", millis, zone);
    }

    /** « À la réception » ou « Par la synchronisation ». */
    public static String via(String source) {
        return Delivery.VIA_SYNC.equals(source) ? "Par la synchronisation" : "À la réception";
    }

    /** « 2 sur 3 réussis » */
    public static String summary(List<Delivery> deliveries) {
        int sent = 0;
        for (Delivery delivery : deliveries) {
            if (delivery.status == DeliveryStatus.SENT) sent++;
        }
        return sent + " sur " + deliveries.size() + (sent > 1 ? " réussis" : " réussi");
    }

    /** Ligne sous le nom de la destination. */
    public static String delivery(Delivery delivery) {
        String attempts = attempts(delivery.attempts);
        switch (delivery.status) {
            case SENT:
                return "Envoyé" + (Delivery.VIA_SYNC.equals(delivery.via) ? " par la synchronisation" : "")
                        + (delivery.httpCode > 0 ? " · HTTP " + delivery.httpCode : "")
                        + (attempts.isEmpty() ? "" : " · " + attempts);
            case FAILED:
                return "Échec" + (delivery.lastError == null ? "" : " · " + delivery.lastError)
                        + (attempts.isEmpty() ? "" : " · " + attempts);
            default:
                return "En attente · reprise par la synchronisation";
        }
    }

    /** Dernier essai d'un envoi en attente : « Essai 2 · sans réponse (délai dépassé) ». */
    public static String lastAttempt(Delivery delivery) {
        if (delivery.attempts <= 0) return "Pas encore essayé";
        String reason = delivery.lastError == null ? "sans réponse"
                : "sans réponse (" + delivery.lastError.substring(0, 1).toLowerCase(Locale.FRENCH)
                + delivery.lastError.substring(1) + ")";
        return "Essai " + delivery.attempts + " · " + reason;
    }

    private static String attempts(int count) {
        if (count <= 0) return "";
        return count + (count > 1 ? " essais" : " essai");
    }

    private static String format(String pattern, long millis, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.FRENCH);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }
}
