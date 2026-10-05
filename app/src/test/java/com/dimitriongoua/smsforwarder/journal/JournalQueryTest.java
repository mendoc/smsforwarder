package com.dimitriongoua.smsforwarder.journal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class JournalQueryTest {
    private static String sql(JournalQuery query, List<String> args) {
        List<String> conditions = new ArrayList<>();
        query.appendConditions(conditions, args);
        return String.join(" AND ", conditions);
    }

    @Test
    public void sansFiltreAucuneCondition() {
        List<String> args = new ArrayList<>();
        assertEquals("", sql(JournalQuery.ALL, args));
        assertTrue(args.isEmpty());
        assertNull(new JournalQuery(null, null, "   ").text);
    }

    @Test
    public void enAttenteCibleLesEnvoisAFaire() {
        List<String> args = new ArrayList<>();
        String where = sql(JournalQuery.ALL.withStatus(JournalQuery.Status.OPEN), args);
        assertEquals("id IN (SELECT sms_id FROM delivery WHERE status IN (?, ?))", where);
        assertEquals("[pending, retry]", args.toString());
    }

    @Test
    public void filtresCombines() {
        List<String> args = new ArrayList<>();
        JournalQuery query = JournalQuery.ALL.withSim(4).withText(" 25 000 ");
        String where = sql(query, args);
        assertEquals("subscription_id = ? AND (sender LIKE ? ESCAPE '\\' OR body LIKE ? ESCAPE '\\')", where);
        assertEquals("[4, %25 000%, %25 000%]", args.toString());
    }

    @Test
    public void choisirUnStatutRetireLaSimMaisGardeLaRecherche() {
        JournalQuery query = JournalQuery.ALL.withSim(4).withText("paypal").withStatus(JournalQuery.Status.FAILED);
        assertNull(query.subscriptionId);
        assertEquals("paypal", query.text);
        assertEquals(JournalQuery.Status.FAILED, query.status);
    }

    @Test
    public void laRechercheEchappeLesJokersSql() {
        assertEquals("100\\% \\_x\\\\", JournalQuery.escapeLike("100% _x\\"));
    }
}
