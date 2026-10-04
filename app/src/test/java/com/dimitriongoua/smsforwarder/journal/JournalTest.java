package com.dimitriongoua.smsforwarder.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.dimitriongoua.smsforwarder.send.SendOutcome;

import org.junit.Test;

import java.util.TimeZone;

public class JournalTest {
    private static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    // 04/10/2026 14:05:09 à Libreville (UTC+1).
    private static final long T = 1791119109000L;

    @Test
    public void lEmpreinteIgnoreLesEspacesAutourDeLExpediteurMaisPasLeCorps() {
        String reference = Fingerprint.of("AirtelMoney", 1000, "Vous avez recu 1000 F");
        assertEquals(64, reference.length());
        assertEquals(reference, Fingerprint.of(" AirtelMoney ", 1000, "Vous avez recu 1000 F"));
        assertNotEquals(reference, Fingerprint.of("AirtelMoney", 1001, "Vous avez recu 1000 F"));
        assertNotEquals(reference, Fingerprint.of("AirtelMoney", 1000, "Vous avez recu 1000 F."));
    }

    @Test
    public void statutDepuisLeResultatDUnEnvoi() {
        assertEquals(DeliveryStatus.SENT, DeliveryStatus.from(SendOutcome.http(200)));
        assertEquals(DeliveryStatus.FAILED, DeliveryStatus.from(SendOutcome.http(500)));
        assertEquals(DeliveryStatus.RETRY, DeliveryStatus.from(SendOutcome.noResponse("x")));
        assertTrue(DeliveryStatus.PENDING.isOpen());
        assertTrue(DeliveryStatus.RETRY.isOpen());
        assertFalse(DeliveryStatus.SENT.isOpen());
        assertFalse(DeliveryStatus.FAILED.isOpen());
        assertEquals(DeliveryStatus.RETRY, DeliveryStatus.fromCode("retry"));
        assertEquals(DeliveryStatus.PENDING, DeliveryStatus.fromCode("inconnu"));
    }

    @Test
    public void extraitSurUneLigneEtTronque() {
        assertEquals("Ligne 1 Ligne 2", JournalFormat.excerpt("Ligne 1\n  Ligne 2 "));
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 200; i++) longText.append('a');
        String excerpt = JournalFormat.excerpt(longText.toString());
        assertEquals(JournalFormat.EXCERPT_LENGTH, excerpt.length());
        assertTrue(excerpt.endsWith("…"));
        assertEquals("", JournalFormat.excerpt(null));
    }

    @Test
    public void ligneDUneDestination() {
        assertEquals("✓ Telegram : envoyé · 1 essai · 04/10 14:05:09",
                JournalFormat.delivery(new Delivery("telegram", "Telegram", DeliveryStatus.SENT, 1, T, null, Delivery.VIA_RECEPTION), LIBREVILLE));
        assertEquals("✓ Telegram : envoyé (synchro) · 3 essais · 04/10 14:05:09",
                JournalFormat.delivery(new Delivery("telegram", "Telegram", DeliveryStatus.SENT, 3, T, "ancienne erreur", Delivery.VIA_SYNC), LIBREVILLE));
        assertEquals("✗ https://x.org/a : en échec · 1 essai · 04/10 14:05:09 · HTTP 500",
                JournalFormat.delivery(new Delivery("url:a", "https://x.org/a", DeliveryStatus.FAILED, 1, T, "HTTP 500", null), LIBREVILLE));
        assertEquals("↻ https://x.org/a : nouvelle tentative · 2 essais · 04/10 14:05:09 · NoConnectionError",
                JournalFormat.delivery(new Delivery("url:a", "https://x.org/a", DeliveryStatus.RETRY, 2, T, "NoConnectionError", null), LIBREVILLE));
        assertEquals("… Telegram : en attente",
                JournalFormat.delivery(new Delivery("telegram", "Telegram", DeliveryStatus.PENDING, 0, 0, null, null), LIBREVILLE));
    }
}
