package com.dimitriongoua.smsforwarder.journal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.TimeZone;

public class HomeFormatTest {
    private static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    private static final long MIN = 60 * 1000L;
    private static final long HOUR = 60 * MIN;
    // Lundi 05/10/2026 14:26 à Libreville (UTC+1)
    private static final long NOW = 1_791_206_760_000L;

    @Test
    public void debutDuJourLocal() {
        assertEquals(NOW - 14 * HOUR - 26 * MIN, HomeFormat.startOfDay(NOW, LIBREVILLE));
    }

    @Test
    public void ilYA() {
        assertEquals("à l'instant", HomeFormat.ago(NOW - 20_000, NOW, LIBREVILLE));
        assertEquals("il y a 4 min", HomeFormat.ago(NOW - 4 * MIN, NOW, LIBREVILLE));
        assertEquals("il y a 2 h", HomeFormat.ago(NOW - 2 * HOUR - 5 * MIN, NOW, LIBREVILLE));
        assertEquals("hier à 21:47", HomeFormat.ago(NOW - 16 * HOUR - 39 * MIN, NOW, LIBREVILLE));
        assertEquals("le 03/10 à 18:12", HomeFormat.ago(NOW - 44 * HOUR - 14 * MIN, NOW, LIBREVILLE));
    }

    @Test
    public void dernierSmsRelaye() {
        JournalEntry entry = new JournalEntry(1, "AirtelMoney", "…", 0, NOW - 4 * MIN, 3, "Am6", Delivery.VIA_RECEPTION);
        assertEquals("Dernier SMS relayé il y a 4 min, AirtelMoney sur Am6.", HomeFormat.lastRelayed(entry, NOW, LIBREVILLE));
        assertEquals("Aucun SMS relayé pour l'instant.", HomeFormat.lastRelayed(null, NOW, LIBREVILLE));
    }

    @Test
    public void envoisEnAttente() {
        assertEquals("1 envoi en attente vers Telegram", HomeFormat.pending(1, Collections.singletonList("Telegram")));
        assertEquals("3 envois en attente vers 2 destinations", HomeFormat.pending(3, Arrays.asList("Miango", "Telegram")));
    }

    @Test
    public void sims() {
        assertEquals("1 sur 2 transférée", HomeFormat.simsForwarded(1, 2));
        assertEquals("2 sur 2 transférées", HomeFormat.simsForwarded(2, 2));
        assertEquals("Transférée · 16 SMS aujourd'hui", HomeFormat.simToday(16));
        assertEquals("Transférée · aucun SMS aujourd'hui", HomeFormat.simToday(0));
        long at = NOW - 19 * MIN;
        assertEquals("Désactivée le 05/10 à 14:07. Ses SMS restent sur le téléphone.", HomeFormat.simDisabled(at, LIBREVILLE));
        assertEquals("Réactivée le 05/10 à 14:07 : les SMS reçus avant cette date ne sont pas transférés.",
                HomeFormat.simChange(true, at, LIBREVILLE));
        assertEquals("", HomeFormat.simChange(true, 0, LIBREVILLE));
    }

    @Test
    public void synchroEtReglages() {
        assertEquals("14:21", HomeFormat.syncTime(NOW - 5 * MIN, NOW, LIBREVILLE));
        assertEquals("04/10 14:26", HomeFormat.syncTime(NOW - 24 * HOUR, NOW, LIBREVILLE));
        assertEquals("—", HomeFormat.syncTime(0, NOW, LIBREVILLE));
        assertEquals("1 autorisation manquante", HomeFormat.missingPermissions(1));
        assertEquals("2 règles", HomeFormat.rules(2));
    }
}
