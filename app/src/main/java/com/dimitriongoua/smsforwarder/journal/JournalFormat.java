package com.dimitriongoua.smsforwarder.journal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Textes affichés dans le journal. Classe sans dépendance Android, testée par JournalFormatTest. */
public final class JournalFormat {
    public static final int EXCERPT_LENGTH = 120;

    private JournalFormat() {
    }

    public static String excerpt(String body) {
        if (body == null) return "";
        String flat = body.replaceAll("\\s+", " ").trim();
        return flat.length() <= EXCERPT_LENGTH ? flat : flat.substring(0, EXCERPT_LENGTH - 1) + "…";
    }

    /** « ✓ Telegram : envoyé (synchro) · 2 essais · 04/10 14:05 · HTTP 500 » */
    public static String delivery(Delivery delivery, TimeZone zone) {
        StringBuilder text = new StringBuilder();
        text.append(symbol(delivery.status)).append(' ')
                .append(delivery.destinationLabel).append(" : ").append(delivery.status.label);
        if (delivery.status == DeliveryStatus.SENT && Delivery.VIA_SYNC.equals(delivery.via)) {
            text.append(" (synchro)");
        }
        if (delivery.attempts > 0) {
            text.append(" · ").append(delivery.attempts).append(delivery.attempts > 1 ? " essais" : " essai");
        }
        if (delivery.lastAttemptAt > 0) text.append(" · ").append(dateTime(delivery.lastAttemptAt, zone));
        if (delivery.status != DeliveryStatus.SENT && delivery.lastError != null) {
            text.append(" · ").append(delivery.lastError);
        }
        return text.toString();
    }

    /** « 14:26 » */
    public static String time(long millis, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.FRENCH);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }

    /** Titre d'un jour du journal : « Aujourd'hui · 05/10 », « Hier · 04/10 », « Samedi 03/10 ». */
    public static String day(long millis, long now, TimeZone zone) {
        SimpleDateFormat date = new SimpleDateFormat("dd/MM", Locale.FRENCH);
        date.setTimeZone(zone);
        int days = daysBetween(millis, now, zone);
        if (days == 0) return "Aujourd'hui · " + date.format(new Date(millis));
        if (days == 1) return "Hier · " + date.format(new Date(millis));
        SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.FRENCH);
        weekday.setTimeZone(zone);
        String name = weekday.format(new Date(millis));
        return Character.toUpperCase(name.charAt(0)) + name.substring(1) + " " + date.format(new Date(millis));
    }

    /** Même jour du calendrier local. */
    public static boolean sameDay(long a, long b, TimeZone zone) {
        return daysBetween(a, b, zone) == 0;
    }

    private static int daysBetween(long earlier, long later, TimeZone zone) {
        return (int) (dayNumber(later, zone) - dayNumber(earlier, zone));
    }

    private static long dayNumber(long millis, TimeZone zone) {
        return Math.floorDiv(millis + zone.getOffset(millis), 24L * 60 * 60 * 1000);
    }

    public static String dateTime(long millis, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.FRENCH);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }

    private static String symbol(DeliveryStatus status) {
        switch (status) {
            case SENT:
                return "✓";
            case FAILED:
                return "✗";
            case RETRY:
                return "↻";
            default:
                return "…";
        }
    }
}
