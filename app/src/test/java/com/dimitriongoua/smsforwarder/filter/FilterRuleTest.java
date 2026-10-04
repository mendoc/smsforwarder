package com.dimitriongoua.smsforwarder.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

public class FilterRuleTest {

    @Test
    public void prefixeFromViseSeulementLExpediteur() throws Exception {
        FilterRule rule = FilterRule.parse("from:^Airtel");
        assertEquals(FilterRule.Target.FROM, rule.getTarget());
        assertFalse(rule.isExclusion());
        assertTrue(rule.matches("AirtelMoney", "x"));
        assertFalse(rule.matches("Moov", "AirtelMoney"));
    }

    @Test
    public void prefixeBodyViseSeulementLeCorps() throws Exception {
        FilterRule rule = FilterRule.parse("body:(?i)paypal");
        assertEquals(FilterRule.Target.BODY, rule.getTarget());
        assertTrue(rule.matches("38880", "Vous avez reçu un paiement PayPal"));
        assertFalse(rule.matches("PayPal", "Code 1234"));
    }

    @Test
    public void sansPrefixeLaRegleViseLExpediteurOuLeCorps() throws Exception {
        FilterRule rule = FilterRule.parse("NETFLIX");
        assertEquals(FilterRule.Target.ANY, rule.getTarget());
        assertTrue(rule.matches("NETFLIX", "Code"));
        assertTrue(rule.matches("38643", "Votre compte NETFLIX"));
        assertFalse(rule.matches("38643", "Autre"));
    }

    @Test
    public void pointDExclamationFaitUneExclusion() throws Exception {
        FilterRule body = FilterRule.parse("!body:(?i)code");
        assertTrue(body.isExclusion());
        assertEquals(FilterRule.Target.BODY, body.getTarget());
        assertTrue(body.matches("x", "Votre CODE est 12"));

        FilterRule any = FilterRule.parse("!promo");
        assertTrue(any.isExclusion());
        assertEquals(FilterRule.Target.ANY, any.getTarget());
        assertEquals("!promo", any.getSource());
    }

    @Test
    public void laRechercheSeFaitNImporteOuEtLesAncresRestentPossibles() throws Exception {
        assertTrue(FilterRule.parse("body:recu").matches("x", "Vous avez recu 1000 F"));
        assertFalse(FilterRule.parse("from:^Money$").matches("AirtelMoney", ""));
        assertTrue(FilterRule.parse("from:^AirtelMoney$").matches("AirtelMoney", ""));
    }

    @Test
    public void unChampAbsentNeCorrespondPas() throws Exception {
        assertFalse(FilterRule.parse("x").matches(null, null));
    }

    @Test
    public void lesEspacesAutourDeLaRegleSontIgnores() throws Exception {
        assertEquals(FilterRule.Target.FROM, FilterRule.parse("  from:Airtel  ").getTarget());
    }

    @Test
    public void regexInvalideRefusee() {
        assertInvalid("body:(paypal");
        assertInvalid("![");
        assertInvalid("from:");
        assertInvalid("!");
        assertInvalid("   ");
    }

    private static void assertInvalid(String line) {
        try {
            FilterRule.parse(line);
            fail("Règle acceptée à tort : " + line);
        } catch (InvalidRuleException e) {
            assertEquals(line.trim(), e.getRule());
        }
    }
}
