package com.dimitriongoua.smsforwarder.activity;

import static com.dimitriongoua.smsforwarder.activity.Screens.MIN;
import static com.dimitriongoua.smsforwarder.activity.Screens.SIM_AM6;
import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.send.SendOutcome;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;
import java.util.TimeZone;

/** Détail d'un SMS AirtelMoney : Miango · reçus envoyé, SMS des SIM en échec (HTTP 400), Telegram en attente. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h940dp-port-xhdpi")
public class DetailScreenshotTest {
    private TimeZone previousZone;
    private long id;

    @Before
    public void seed() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(Screens.LIBREVILLE);
        JournalDb.resetForTests();
        JournalDb journal = JournalDb.get(Screens.app());
        long now = System.currentTimeMillis();
        id = Screens.record(journal, "AirtelMoney",
                "Vous avez recu 25 000 FCFA du 07 •• •• 31. Nouveau solde : 437 350 FCFA. TID: PP251005.1426.A84213",
                now - 4 * MIN, Delivery.VIA_RECEPTION, SIM_AM6);
        Screens.attempt(journal, id, Screens.MIANGO, SendOutcome.http(200));
        Screens.attempt(journal, id, Screens.SIMS, SendOutcome.http(400));
        Screens.attempt(journal, id, Screens.TELEGRAM, SendOutcome.noResponse("Délai dépassé"));
        Screens.attempt(journal, id, Screens.TELEGRAM, SendOutcome.noResponse("Délai dépassé"));
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
        TimeZone.setDefault(previousZone);
    }

    private Activity open() {
        Intent intent = new Intent(Screens.app(), DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, id);
        Activity activity = Robolectric.buildActivity(DetailActivity.class, intent).setup().get();
        Screens.waitFor(() -> ((LinearLayout) activity.findViewById(R.id.detail_deliveries)).getChildCount() == 3);
        return activity;
    }

    @Test
    public void detail() throws IOException {
        Activity activity = open();
        assertEquals("AirtelMoney", text(activity, R.id.detail_sender));
        assertEquals("1 sur 3 réussi", text(activity, R.id.detail_summary));
        LinearLayout deliveries = activity.findViewById(R.id.detail_deliveries);
        assertEquals("Miango · reçus", ((TextView) deliveries.getChildAt(0).findViewById(R.id.delivery_label)).getText().toString());
        assertEquals("Envoyé · HTTP 200 · 1 essai",
                ((TextView) deliveries.getChildAt(0).findViewById(R.id.delivery_detail)).getText().toString());
        // « Réessayer » sur chaque envoi non réussi, pas sur l'envoi réussi.
        assertEquals(View.GONE, deliveries.getChildAt(0).findViewById(R.id.delivery_retry).getVisibility());
        assertEquals(View.VISIBLE, deliveries.getChildAt(1).findViewById(R.id.delivery_retry).getVisibility());
        assertEquals(View.VISIBLE, deliveries.getChildAt(2).findViewById(R.id.delivery_retry).getVisibility());
        assertEquals("AirtelMoney", ((TextView) activity.findViewById(R.id.detail_fact_rule)
                .findViewById(R.id.fact_value)).getText().toString());
        Screens.capture(activity, "detail");
    }

    private static String text(Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
