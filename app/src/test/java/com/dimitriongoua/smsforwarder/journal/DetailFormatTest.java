package com.dimitriongoua.smsforwarder.journal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.TimeZone;

public class DetailFormatTest {
    private static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    // Lundi 05/10/2026 14:26:02 à Libreville
    private static final long AT = 1_791_206_762_000L;

    private static Delivery d(String label, DeliveryStatus status, int attempts, String error, String via) {
        return new Delivery(label, label, status, attempts, AT, error, via);
    }

    @Test
    public void enTete() {
        assertEquals("05/10 · 14:26:02", DetailFormat.received(AT, LIBREVILLE));
        assertEquals("Par la synchronisation", DetailFormat.via(Delivery.VIA_SYNC));
        assertEquals("À la réception", DetailFormat.via(Delivery.VIA_RECEPTION));
    }

    @Test
    public void envois() {
        Delivery sent = d("Miango", DeliveryStatus.SENT, 1, null, Delivery.VIA_RECEPTION);
        Delivery open = d("Telegram", DeliveryStatus.RETRY, 2, "Délai dépassé", null);
        Delivery failed = d("Autre", DeliveryStatus.FAILED, 1, "HTTP 400", null);
        assertEquals("Envoyé · 1 essai", DetailFormat.delivery(sent));
        assertEquals("Envoyé · HTTP 200 · 1 essai", DetailFormat.delivery(new Delivery("url:a", "Miango",
                DeliveryStatus.SENT, 1, AT, null, Delivery.VIA_RECEPTION, 200)));
        assertEquals("Envoyé par la synchronisation · 3 essais",
                DetailFormat.delivery(d("Miango", DeliveryStatus.SENT, 3, null, Delivery.VIA_SYNC)));
        assertEquals("En attente · reprise par la synchronisation", DetailFormat.delivery(open));
        assertEquals("Échec · HTTP 400 · 1 essai", DetailFormat.delivery(failed));
        assertEquals("Essai 2 · sans réponse (délai dépassé)", DetailFormat.lastAttempt(open));
        assertEquals("1 sur 3 réussi", DetailFormat.summary(Arrays.asList(sent, open, failed)));
    }
}
