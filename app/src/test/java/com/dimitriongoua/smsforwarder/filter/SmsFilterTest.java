package com.dimitriongoua.smsforwarder.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SmsFilterTest {
    private static final List<String> SENDERS = Arrays.asList("AirtelMoney", "Paypal", "NETFLIX", "38643", "38880");

    private static SmsFilter filter(String... rules) {
        return SmsFilter.fromSettings(SENDERS, Arrays.asList(rules));
    }

    @Test
    public void lesReglesParDefautReproduisentLaV120() {
        SmsFilter filter = filter(SmsFilter.DEFAULT_RULES);
        // Expéditeur autorisé, sans tenir compte de la casse ni des espaces.
        assertTrue(filter.accepts("AirtelMoney", "Vous avez recu 1000 F"));
        assertTrue(filter.accepts(" airtelmoney ", "x"));
        // Corps contenant « paypal », quel que soit l'expéditeur.
        assertTrue(filter.accepts("+33600000000", "Paiement PAYPAL reçu"));
        // Ni l'un ni l'autre.
        assertFalse(filter.accepts("+24177000000", "Bonjour"));
        assertFalse(filter.accepts(null, "Bonjour"));
    }

    @Test
    public void uneRegleDInclusionRelaieUnSmsHorsListe() {
        SmsFilter filter = filter("from:^Moov");
        assertTrue(filter.accepts("MoovMoney", "x"));
        assertFalse(filter.accepts("Orange", "x"));
    }

    @Test
    public void lExclusionEstPrioritaireSurLaListeEtLesInclusions() {
        SmsFilter filter = filter("body:(?i)paypal", "!body:(?i)code de vérification");
        assertFalse(filter.accepts("AirtelMoney", "Votre code de vérification : 1234"));
        assertFalse(filter.accepts("x", "PayPal : code de vérification 99"));
        assertTrue(filter.accepts("AirtelMoney", "Vous avez recu 1000 F"));
    }

    @Test
    public void uneExclusionSansPrefixeVisentLesDeuxChamps() {
        SmsFilter filter = filter("!NETFLIX");
        assertFalse(filter.accepts("NETFLIX", "x"));
        assertFalse(filter.accepts("AirtelMoney", "abonnement netflix NETFLIX"));
        assertTrue(filter.accepts("AirtelMoney", "x"));
    }

    @Test
    public void sansRegleSeuleLaListeCompte() {
        SmsFilter filter = SmsFilter.fromSettings(SENDERS, Collections.<String>emptyList());
        assertTrue(filter.accepts("38880", "x"));
        assertFalse(filter.accepts("x", "paypal"));
    }

    @Test
    public void uneRegleInvalideEnregistreeEstIgnoreeSansPlanter() {
        SmsFilter filter = filter("body:(paypal", "", "body:(?i)paypal");
        assertEquals(1, filter.getRules().size());
        assertTrue(filter.accepts("x", "paypal"));
    }

    @Test
    public void validateSignaleChaqueRegleInvalideEtIgnoreLesLignesVides() {
        List<InvalidRuleException> errors = SmsFilter.validate(Arrays.asList("body:(?i)paypal", "", "from:[", "  ", "!"));
        assertEquals(2, errors.size());
        assertEquals("from:[", errors.get(0).getRule());
        assertEquals("!", errors.get(1).getRule());
        assertTrue(SmsFilter.validate(Arrays.asList("body:ok", "!from:^x$")).isEmpty());
    }

    @Test
    public void raisonDuRelais() {
        SmsFilter filter = SmsFilter.fromSettings(java.util.Arrays.asList("AirtelMoney"), java.util.Arrays.asList("body:(?i)paypal"));
        assertEquals("AirtelMoney", filter.reason("airtelmoney", "Votre solde"));
        assertEquals("body:(?i)paypal", filter.reason("38643", "Code PayPal"));
        assertEquals(null, filter.reason("Inconnu", "Bonjour"));
    }
}
