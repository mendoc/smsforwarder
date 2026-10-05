package com.dimitriongoua.smsforwarder.journal;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Textes de l'Accueil, de la Carte SIM et des Réglages (maquette de la refonte).
 * Classe sans dépendance Android, testée par HomeFormatTest.
 */
public final class HomeFormat {
    private static final long MINUTE = 60 * 1000L;
    private static final long HOUR = 60 * MINUTE;

    private HomeFormat() {
    }

    /** Début du jour local qui contient {@code now}. */
    public static long startOfDay(long now, TimeZone zone) {
        long day = 24 * HOUR;
        long local = now + zone.getOffset(now);
        long start = local - Math.floorMod(local, day);
        return start - zone.getOffset(start);
    }

    /** « à l'instant », « il y a 4 min », « il y a 2 h », « hier à 21:47 », « le 03/10 à 18:12 ». */
    public static String ago(long then, long now, TimeZone zone) {
        long elapsed = Math.max(0, now - then);
        if (elapsed < MINUTE) return "à l'instant";
        if (JournalFormat.sameDay(then, now, zone)) {
            if (elapsed < HOUR) return "il y a " + (elapsed / MINUTE) + " min";
            return "il y a " + (elapsed / HOUR) + " h";
        }
        if (JournalFormat.sameDay(then, now - 24 * HOUR, zone)) return "hier à " + JournalFormat.time(then, zone);
        return "le " + format("dd/MM", then, zone) + " à " + JournalFormat.time(then, zone);
    }

    /** « Dernier SMS relayé il y a 4 min, AirtelMoney sur Am6. » */
    public static String lastRelayed(JournalEntry entry, long now, TimeZone zone) {
        if (entry == null) return "Aucun SMS relayé pour l'instant.";
        return "Dernier SMS relayé " + ago(entry.receivedAt, now, zone) + ", " + entry.sender + " sur " + entry.simLabel + ".";
    }

    /** « 1 envoi en attente vers Telegram », « 3 envois en attente vers 2 destinations ». */
    public static String pending(int open, List<String> destinations) {
        String count = open + (open > 1 ? " envois en attente" : " envoi en attente");
        if (destinations.size() == 1) return count + " vers " + destinations.get(0);
        if (destinations.size() > 1) return count + " vers " + destinations.size() + " destinations";
        return count;
    }

    /** « 1 sur 2 transférée », « 2 sur 2 transférées ». */
    public static String simsForwarded(int enabled, int total) {
        return enabled + " sur " + total + (enabled > 1 ? " transférées" : " transférée");
    }

    /** « Transférée · 16 SMS aujourd'hui », « Transférée · aucun SMS aujourd'hui ». */
    public static String simToday(int count) {
        return "Transférée · " + (count == 0 ? "aucun" : String.valueOf(count)) + " SMS aujourd'hui";
    }

    /** « Désactivée le 05/10 à 14:07. Ses SMS restent sur le téléphone. » */
    public static String simDisabled(long changedAt, TimeZone zone) {
        String when = changedAt > 0 ? " le " + dayAt(changedAt, zone) : "";
        return "Désactivée" + when + ". Ses SMS restent sur le téléphone.";
    }

    /** Note de la Carte SIM : réactivation ou désactivation, vide si la SIM n'a jamais été réglée. */
    public static String simChange(boolean enabled, long changedAt, TimeZone zone) {
        if (changedAt <= 0) return "";
        return enabled
                ? "Réactivée le " + dayAt(changedAt, zone) + " : les SMS reçus avant cette date ne sont pas transférés."
                : "Désactivée le " + dayAt(changedAt, zone) + " : ses SMS ne sont pas transférés.";
    }

    /** Heure de synchronisation : « 14:21 » aujourd'hui, « 04/10 14:21 » sinon, « — » jamais. */
    public static String syncTime(long millis, long now, TimeZone zone) {
        if (millis <= 0) return "—";
        if (JournalFormat.sameDay(millis, now, zone)) return JournalFormat.time(millis, zone);
        return format("dd/MM", millis, zone) + " " + JournalFormat.time(millis, zone);
    }

    /** Identifiant du téléphone raccourci pour l'affichage : « a-3f9c…e1d0 ». */
    public static String shortId(String id) {
        if (id == null) return "";
        return id.length() <= 12 ? id : id.substring(0, 6) + "…" + id.substring(id.length() - 4);
    }

    /** « 1 autorisation manquante », « 2 autorisations manquantes ». */
    public static String missingPermissions(int missing) {
        return missing + (missing > 1 ? " autorisations manquantes" : " autorisation manquante");
    }

    /** « 1 règle », « 2 règles », « aucune règle ». */
    public static String rules(int count) {
        if (count == 0) return "aucune règle";
        return count + (count > 1 ? " règles" : " règle");
    }

    private static String dayAt(long millis, TimeZone zone) {
        return format("dd/MM", millis, zone) + " à " + JournalFormat.time(millis, zone);
    }

    private static String format(String pattern, long millis, TimeZone zone) {
        SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.FRENCH);
        format.setTimeZone(zone);
        return format.format(new Date(millis));
    }
}
