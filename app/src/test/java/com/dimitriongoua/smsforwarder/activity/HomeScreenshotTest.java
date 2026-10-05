package com.dimitriongoua.smsforwarder.activity;

import static com.dimitriongoua.smsforwarder.activity.Screens.MIN;
import static com.dimitriongoua.smsforwarder.activity.Screens.SIM_AM6;
import static com.dimitriongoua.smsforwarder.activity.Screens.SIM_MOOV;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.HomeFormat;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.send.SendOutcome;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.widget.Toggle;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;
import java.util.TimeZone;

/** Accueil avec deux SIM (Am6 active, Moov désactivée) et un envoi Telegram en attente. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h1160dp-port-xhdpi")
public class HomeScreenshotTest {
    private TimeZone previousZone;

    @Before
    public void seed() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(Screens.LIBREVILLE);
        JournalDb.resetForTests();
        Screens.grantAll();
        Screens.twoSims();
        Settings settings = Settings.with(Screens.app());
        settings.setDeviceName("Galaxy A14 · Bureau");
        settings.setSimName(SIM_AM6, "Am6");
        settings.setSimName(SIM_MOOV, "Moov · perso");
        settings.setSimEnabled(SIM_MOOV, false, Screens.at(0, 14, 7));
        long now = System.currentTimeMillis();
        settings.setSyncResult(now - 9 * MIN, now - 2 * MIN);

        JournalDb journal = JournalDb.get(Screens.app());
        long a = Screens.record(journal, "AirtelMoney", "Vous avez recu 25 000 FCFA du 07 •• •• 31.", now - 4 * MIN,
                Delivery.VIA_RECEPTION, SIM_AM6);
        Screens.attempt(journal, a, Screens.MIANGO, SendOutcome.http(200));
        Screens.attempt(journal, a, Screens.SIMS, SendOutcome.http(200));
        Screens.attempt(journal, a, Screens.TELEGRAM, SendOutcome.noResponse("Délai dépassé"));
        Screens.allSent(journal, Screens.record(journal, "AirtelMoney", "Votre solde est 412 350 FCFA.", now - 28 * MIN,
                Delivery.VIA_RECEPTION, SIM_AM6));
        Screens.allSent(journal, Screens.record(journal, "38643", "Vous avez envoye 15 000 FCFA.", now - 50 * MIN,
                Delivery.VIA_SYNC, SIM_AM6));
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
        TimeZone.setDefault(previousZone);
    }

    private static Activity open() {
        Activity activity = Robolectric.buildActivity(MainActivity.class).setup().get();
        Screens.waitFor(() -> ((LinearLayout) activity.findViewById(R.id.home_sims)).getChildCount() == 2
                && activity.findViewById(R.id.home_alert).getVisibility() == View.VISIBLE);
        return activity;
    }

    @Test
    public void accueil() throws IOException {
        Activity activity = open();
        assertEquals("Galaxy A14 · Bureau", text(activity, R.id.home_device));
        // 3 SMS relayés (il y a 4, 28 et 50 min), moins ceux de la veille si le test tourne juste après minuit
        long start = HomeFormat.startOfDay(System.currentTimeMillis(), Screens.LIBREVILLE);
        int expected = 0;
        for (long ago : new long[]{4, 28, 50}) if (System.currentTimeMillis() - ago * MIN >= start) expected++;
        assertEquals(String.valueOf(expected), text(activity, R.id.home_stat_relayed));
        assertEquals("1", text(activity, R.id.home_stat_pending));
        assertEquals("0", text(activity, R.id.home_stat_failed));
        assertEquals("1 envoi en attente vers Telegram", text(activity, R.id.home_alert_title));
        assertEquals("Dernier SMS relayé il y a 4 min, AirtelMoney sur Am6.", text(activity, R.id.home_last));
        assertEquals("1 sur 2 transférée", text(activity, R.id.home_sims_count));
        assertEquals("1 en attente", text(activity, R.id.home_sync_badge));
        Screens.capture(activity, "accueil");
    }

    @Test
    public void activerUneSimDepuisLAccueil() {
        Activity activity = open();
        LinearLayout sims = activity.findViewById(R.id.home_sims);
        Toggle moov = sims.getChildAt(1).findViewById(R.id.sim_toggle);
        assertFalse(moov.isChecked());
        moov.performClick();
        assertTrue(Settings.with(Screens.app()).getSimPolicy().isEnabled(SIM_MOOV));
        Screens.waitFor(() -> "2 sur 2 transférées".equals(text(activity, R.id.home_sims_count)));
        assertEquals("2 sur 2 transférées", text(activity, R.id.home_sims_count));
    }

    @Test
    public void lAlerteOuvreLeDetailDuSmsEnAttente() {
        Activity activity = open();
        activity.findViewById(R.id.home_alert).performClick();
        Intent next = Shadows.shadowOf(activity).getNextStartedActivity();
        assertEquals(DetailActivity.class.getName(), next.getComponent().getClassName());
        assertTrue(next.getLongExtra(DetailActivity.EXTRA_ID, -1) > 0);
    }

    private static String text(Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
