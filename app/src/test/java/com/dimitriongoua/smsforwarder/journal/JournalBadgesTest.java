package com.dimitriongoua.smsforwarder.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class JournalBadgesTest {
    private static JournalEntry entry(String source, Delivery... deliveries) {
        JournalEntry entry = new JournalEntry(1, "AirtelMoney", "Votre solde est 412 350 FCFA.", 0, 0, 3, "Am6", source);
        for (Delivery delivery : deliveries) entry.deliveries.add(delivery);
        return entry;
    }

    private static Delivery delivery(String label, DeliveryStatus status, int attempts, String error) {
        return new Delivery(label.toLowerCase(), label, status, attempts, 0, error, null);
    }

    @Test
    public void tousLesEnvoisReussisSontResumes() {
        JournalEntry e = entry(Delivery.VIA_RECEPTION,
                delivery("Miango", DeliveryStatus.SENT, 1, null),
                delivery("SMS des SIM", DeliveryStatus.SENT, 1, null),
                delivery("Telegram", DeliveryStatus.SENT, 1, null));
        assertEquals("[SIM:Am6, SENT:3 destinations]", JournalBadges.of(e).toString());
        assertFalse(JournalBadges.hasOpen(e));
    }

    @Test
    public void unEnvoiEnAttenteDetailleChaqueDestination() {
        JournalEntry e = entry(Delivery.VIA_RECEPTION,
                delivery("Miango", DeliveryStatus.SENT, 1, null),
                delivery("Telegram", DeliveryStatus.RETRY, 2, "Délai dépassé"));
        assertEquals("[SIM:Am6, SENT:Miango, OPEN:Telegram · 2 essais]", JournalBadges.of(e).toString());
        assertTrue(JournalBadges.hasOpen(e));
    }

    @Test
    public void unEchecMontreSonErreurRaccourcie() {
        JournalEntry e = entry(Delivery.VIA_SYNC,
                delivery("Telegram", DeliveryStatus.SENT, 1, null),
                delivery("Miango", DeliveryStatus.FAILED, 1, "HTTP 400"),
                delivery("Autre", DeliveryStatus.FAILED, 1, "Nom de domaine introuvable : exemple.invalid"));
        assertEquals("[SIM:Am6, SYNC:Repris par synchro, SENT:Telegram, FAILED:Miango · HTTP 400, "
                + "FAILED:Autre · Nom de domaine introuva…]", JournalBadges.of(e).toString());
    }

    @Test
    public void uneSeuleDestinationReussieGardeSonNom() {
        assertEquals("[SIM:Am6, SENT:Telegram]",
                JournalBadges.of(entry(Delivery.VIA_RECEPTION, delivery("Telegram", DeliveryStatus.SENT, 1, null))).toString());
        assertEquals("[SIM:Am6, NONE:Aucune destination]", JournalBadges.of(entry(Delivery.VIA_RECEPTION)).toString());
    }
}
