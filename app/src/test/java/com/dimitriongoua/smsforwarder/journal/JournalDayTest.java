package com.dimitriongoua.smsforwarder.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.TimeZone;

public class JournalDayTest {
    private static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    private static final long HOUR = 60 * 60 * 1000L;
    // Lundi 05/10/2026 14:26 à Libreville (UTC+1)
    private static final long NOW = 1_791_206_760_000L;

    @Test
    public void aujourdhuiHierPuisLeJourDeLaSemaine() {
        assertEquals("Aujourd'hui · 05/10", JournalFormat.day(NOW - 2 * HOUR, NOW, LIBREVILLE));
        assertEquals("Hier · 04/10", JournalFormat.day(NOW - 24 * HOUR, NOW, LIBREVILLE));
        assertEquals("Samedi 03/10", JournalFormat.day(NOW - 48 * HOUR, NOW, LIBREVILLE));
        assertEquals("14:26", JournalFormat.time(NOW, LIBREVILLE));
    }

    @Test
    public void minuitLocalSepareLesJours() {
        // 00:30 et 23:30 locales du même jour, puis la veille à 23:30
        long midnight = NOW - 14 * HOUR - 26 * 60 * 1000L;
        assertTrue(JournalFormat.sameDay(midnight + HOUR / 2, midnight + 23 * HOUR + HOUR / 2, LIBREVILLE));
        assertFalse(JournalFormat.sameDay(midnight - HOUR / 2, midnight + HOUR / 2, LIBREVILLE));
    }
}
