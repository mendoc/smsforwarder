package com.dimitriongoua.smsforwarder.destination;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class UrlDestinationTest {

    @Test
    public void seulesLesUrlHttpAbsoluesSontAcceptees() {
        assertNull(UrlDestination.validateUrl("https://miango.netlify.app/sms/incoming"));
        assertNull(UrlDestination.validateUrl("  http://192.168.1.10:8080/sms  "));
        assertNotNull(UrlDestination.validateUrl(""));
        assertNotNull(UrlDestination.validateUrl(null));
        assertNotNull(UrlDestination.validateUrl("/smshandler"));
        assertNotNull(UrlDestination.validateUrl("miango.netlify.app/smshandler"));
        assertNotNull(UrlDestination.validateUrl("ftp://exemple.com/x"));
        assertNotNull(UrlDestination.validateUrl("https://"));
        assertNotNull(UrlDestination.validateUrl("https://exemple .com"));
    }

    @Test
    public void leNomDEnTeteEstFacultatifMaisDoitEtreValide() {
        assertNull(UrlDestination.validateHeaderName(null));
        assertNull(UrlDestination.validateHeaderName("  "));
        assertNull(UrlDestination.validateHeaderName("X-Sms-Token"));
        assertNotNull(UrlDestination.validateHeaderName("X Sms Token"));
        assertNotNull(UrlDestination.validateHeaderName("X-Token:"));
    }

    @Test
    public void sansNomDEnTeteLaValeurNestPasConservee() {
        UrlDestination destination = new UrlDestination("a", "https://x.org", " ", "secret", true);
        assertFalse(destination.hasHeader());
        assertNull(destination.getHeaderValue());
        UrlDestination withHeader = new UrlDestination("a", "https://x.org", " X-Sms-Token ", "secret", true);
        assertTrue(withHeader.hasHeader());
        assertEquals("X-Sms-Token", withHeader.getHeaderName());
        assertEquals("secret", withHeader.getHeaderValue());
    }

    @Test
    public void leLibelleMasqueLaRequeteQuiPeutContenirUnSecret() {
        assertEquals("https://miango.netlify.app/smshandler",
                new UrlDestination("a", "https://miango.netlify.app/smshandler", null, null, true).getLabel());
        assertEquals("https://exemple.com:8443/hook?…",
                new UrlDestination("a", "https://exemple.com:8443/hook?token=abc", null, null, true).getLabel());
    }

    @Test
    public void identifiantGenereEtCleDuJournal() {
        UrlDestination destination = new UrlDestination(null, "https://x.org", null, null, true);
        assertFalse(destination.getId().isEmpty());
        assertEquals("url:smshandler", new UrlDestination("smshandler", "https://x.org", null, null, true).getKey());
        assertFalse(destination.withEnabled(false).isEnabled());
        assertEquals(destination.getId(), destination.withEnabled(false).getId());
    }

    @Test
    public void validateRenvoieLaPremiereErreur() {
        assertNull(new UrlDestination("a", "https://x.org", "X-Token", "v", true).validate());
        assertNotNull(new UrlDestination("a", "x.org", null, null, true).validate());
        assertNotNull(new UrlDestination("a", "https://x.org", "X Token", "v", true).validate());
    }

    @Test
    public void lesSecretsSontMasques() {
        assertEquals("Erreur *** sur https://api.telegram.org/bot***/sendMessage",
                Secrets.redact("Erreur 123456:ABC sur https://api.telegram.org/bot123456:ABC/sendMessage",
                        Arrays.asList("123456:ABC", null, "")));
        // Un secret trop court n'est pas remplacé (il masquerait n'importe quel texte).
        assertEquals("abc", Secrets.redact("abc", Arrays.asList("a")));
        assertEquals(Secrets.MASK, Secrets.mask("secret"));
        assertEquals("", Secrets.mask(""));
        assertEquals("", Secrets.mask(null));
    }

    @Test
    public void telegramActifSeulementSiCompletEtActive() {
        assertTrue(new TelegramDestination("t", "c", true).isActive());
        assertFalse(new TelegramDestination("t", "c", false).isActive());
        assertFalse(new TelegramDestination("", "c", true).isActive());
        assertFalse(new TelegramDestination("t", " ", true).isActive());
    }
}
