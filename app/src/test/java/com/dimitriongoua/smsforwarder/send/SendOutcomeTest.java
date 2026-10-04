package com.dimitriongoua.smsforwarder.send;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SendOutcomeTest {

    @Test
    public void uneReponse2xxEstUnEnvoiReussi() {
        SendOutcome outcome = SendOutcome.http(201);
        assertTrue(outcome.isSent());
        assertNull(outcome.getError());
    }

    @Test
    public void uneReponseDErreurEstDefinitive() {
        // /smshandler répond 500 aux SMS qui ne viennent pas d'AirtelMoney, et peut avoir
        // mis à jour le solde avant une erreur : pas de nouvel envoi automatique.
        for (int code : new int[]{400, 401, 404, 429, 500, 503}) {
            SendOutcome outcome = SendOutcome.http(code);
            assertEquals(SendOutcome.Status.FAILED, outcome.getStatus());
            assertFalse(outcome.shouldRetry());
            assertEquals("HTTP " + code, outcome.getError());
        }
    }

    @Test
    public void sansReponseLeSmsSeraRenvoye() {
        SendOutcome outcome = SendOutcome.noResponse("NoConnectionError");
        assertTrue(outcome.shouldRetry());
        assertEquals("NoConnectionError", outcome.getError());
        assertEquals("Pas de réponse", SendOutcome.noResponse(null).getError());
    }

    @Test
    public void uneDestinationInvalideNEstPasReessayee() {
        SendOutcome outcome = SendOutcome.invalid("URL manquante");
        assertEquals(SendOutcome.Status.FAILED, outcome.getStatus());
        assertFalse(outcome.shouldRetry());
    }
}
