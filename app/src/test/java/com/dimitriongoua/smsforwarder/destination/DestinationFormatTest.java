package com.dimitriongoua.smsforwarder.destination;

import static org.junit.Assert.assertEquals;

import com.dimitriongoua.smsforwarder.send.SendOutcome;

import org.junit.Test;

import java.util.TimeZone;

public class DestinationFormatTest {
    private static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    private static final long MIN = 60 * 1000L;
    // Lundi 05/10/2026 14:26 à Libreville
    private static final long NOW = 1_791_206_760_000L;

    @Test
    public void nomsEtUrl() {
        assertEquals("Miango · reçus", DestinationFormat.name(new UrlDestination(DestinationStore.SMS_HANDLER_ID,
                "https://miango.netlify.app/smshandler", null, null, true)));
        assertEquals("Miango · SMS des SIM", DestinationFormat.name(new UrlDestination(DestinationStore.SMS_INCOMING_ID,
                "https://miango.netlify.app/sms/incoming", "X-Sms-Token", "s", true)));
        assertEquals("exemple.com", DestinationFormat.name(new UrlDestination("x", "https://exemple.com/sms?k=1", null, null, true)));
        assertEquals("miango.netlify.app/smshandler", DestinationFormat.shortUrl("https://miango.netlify.app/smshandler"));
        assertEquals("X-Sms-Token : ••••••••", DestinationFormat.header("X-Sms-Token"));
        assertEquals("Banque", DestinationFormat.name(new UrlDestination(DestinationStore.SMS_HANDLER_ID, "Banque",
                "https://miango.netlify.app/smshandler", null, null, true)));
        assertEquals("exemple.com", DestinationFormat.name(new UrlDestination("x", "  ", "https://exemple.com/sms", null, null, true)));
    }

    @Test
    public void titreDUnEnvoi() {
        java.util.Map<String, String> names = new java.util.HashMap<>();
        names.put("url:a", "Banque");
        names.put("telegram", "Telegram");
        assertEquals("Banque", DestinationFormat.label(names, "url:a", "https://banque.example/sms"));
        assertEquals("banque.example", DestinationFormat.label(names, "url:supprimee", "https://banque.example/sms"));
        assertEquals("Telegram", DestinationFormat.label(names, "telegram", "Telegram"));
        assertEquals("Autre", DestinationFormat.label(null, "autre", "Autre"));
    }

    @Test
    public void telegramSansLeToken() {
        assertEquals("Bot ••••7Hk · conversation -100123", DestinationFormat.telegram("123456:ABCdef7Hk", "-100123"));
        assertEquals("Token du bot et conversation à renseigner", DestinationFormat.telegram("default_value", "default_value"));
        assertEquals("Token du bot à renseigner · conversation 42", DestinationFormat.telegram("", "42"));
    }

    @Test
    public void piedDeCarte() {
        assertEquals("1 envoi en attente, sans réponse", DestinationFormat.status(1, NOW, NOW, LIBREVILLE));
        assertEquals("Dernier envoi réussi à 14:26", DestinationFormat.status(0, NOW, NOW, LIBREVILLE));
        assertEquals("Dernier envoi réussi le 04/10 à 14:26", DestinationFormat.status(0, NOW - 24 * 60 * MIN, NOW, LIBREVILLE));
        assertEquals("Aucun envoi réussi pour l'instant", DestinationFormat.status(0, 0, NOW, LIBREVILLE));
    }

    @Test
    public void resultatDuTest() {
        assertEquals("Destination joignable (réponse HTTP 400)", DestinationFormat.test(false, SendOutcome.http(400)));
        assertEquals("Accès refusé : vérifiez l'en-tête (HTTP 401)", DestinationFormat.test(false, SendOutcome.http(401)));
        assertEquals("Injoignable : Délai dépassé", DestinationFormat.test(false, SendOutcome.noResponse("Délai dépassé")));
        assertEquals("Message de test envoyé sur Telegram", DestinationFormat.test(true, SendOutcome.http(200)));
        assertEquals("Token du bot refusé (HTTP 401)", DestinationFormat.test(true, SendOutcome.http(401)));
    }
}
