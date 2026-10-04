package com.dimitriongoua.smsforwarder.send;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TelegramMessageTest {

    @Test
    public void laSimEtLeTelephoneSuiventLExpediteur() {
        assertEquals("Vous avez recu 25 000 F\n\nAirtelMoney\nAM2 · Téléphone bureau\n04-10-2026 à 14:05",
                TelegramMessage.format("Vous avez recu 25 000 F", "AirtelMoney", "AM2", "Téléphone bureau", "04-10-2026 à 14:05"));
    }

    @Test
    public void lesInformationsAbsentesSontOmises() {
        assertEquals("x\n\n38643\nTéléphone bureau\n04-10-2026 à 14:05",
                TelegramMessage.format("x", "38643", null, "Téléphone bureau", "04-10-2026 à 14:05"));
        assertEquals("x\n\n38643\nAM2", TelegramMessage.format("x", "38643", "AM2", " ", ""));
    }

    @Test
    public void nomDeLaSim() {
        assertEquals("AM2", TelegramMessage.simLabel(" AM2 ", 0, "Airtel"));
        assertEquals("SIM 2 · Moov", TelegramMessage.simLabel(null, 1, "Moov"));
        assertEquals("SIM 1", TelegramMessage.simLabel("", 0, null));
        assertEquals("SIM inconnue", TelegramMessage.simLabel(null, -1, null));
    }
}
