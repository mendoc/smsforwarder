package com.dimitriongoua.smsforwarder.activity;

import static com.dimitriongoua.smsforwarder.activity.Screens.MIN;
import static com.dimitriongoua.smsforwarder.activity.Screens.SIM_AM6;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Activity;
import android.content.Intent;
import android.widget.EditText;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.widget.Toggle;

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

/** Carte SIM « Am6 », réactivée à 16:40, comme sur la maquette. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h960dp-port-xhdpi")
public class SimScreenshotTest {
    private TimeZone previousZone;

    @Before
    public void seed() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(Screens.LIBREVILLE);
        JournalDb.resetForTests();
        Screens.grantAll();
        Screens.twoSims();
        Settings settings = Settings.with(Screens.app());
        settings.setSimName(SIM_AM6, "Am6");
        settings.setSimEnabled(SIM_AM6, false, Screens.at(0, 9, 0));
        settings.setSimEnabled(SIM_AM6, true, Screens.at(0, 16, 40));
        JournalDb journal = JournalDb.get(Screens.app());
        long now = System.currentTimeMillis();
        Screens.allSent(journal, Screens.record(journal, "AirtelMoney", "Votre solde est 412 350 FCFA.", now - 3 * MIN,
                Delivery.VIA_RECEPTION, SIM_AM6));
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
        TimeZone.setDefault(previousZone);
    }

    private static Activity open() {
        Intent intent = new Intent(Screens.app(), SimActivity.class).putExtra(SimActivity.EXTRA_SUBSCRIPTION_ID, SIM_AM6);
        Activity activity = Robolectric.buildActivity(SimActivity.class, intent).setup().get();
        Screens.waitFor(() -> !"".contentEquals(((TextView) activity.findViewById(R.id.simd_last)).getText()));
        return activity;
    }

    @Test
    public void carteSim() throws IOException {
        Activity activity = open();
        assertEquals("SIM 1 · emplacement 1", text(activity, R.id.simd_slot));
        assertEquals("Am6", text(activity, R.id.simd_name));
        assertEquals("Airtel · +241 07 •• •• 12", text(activity, R.id.simd_sub));
        assertEquals("1", text(activity, R.id.simd_today));
        assertEquals("Réactivée le " + new java.text.SimpleDateFormat("dd/MM", java.util.Locale.FRENCH)
                .format(new java.util.Date(Screens.at(0, 16, 40))) + " à 16:40 : les SMS reçus avant cette date ne sont pas transférés.",
                text(activity, R.id.simd_note));
        Screens.capture(activity, "carte-sim");
    }

    @Test
    public void desactiverEtRenommer() {
        Activity activity = open();
        Toggle toggle = activity.findViewById(R.id.simd_toggle);
        toggle.performClick();
        assertFalse(Settings.with(Screens.app()).getSimPolicy().isEnabled(SIM_AM6));
        ((EditText) activity.findViewById(R.id.simd_name_field)).setText("Airtel bureau");
        activity.findViewById(R.id.simd_save).performClick();
        assertEquals("Airtel bureau", Settings.with(Screens.app()).getSimName(SIM_AM6));
        assertEquals("Airtel bureau", text(activity, R.id.simd_name));
    }

    private static String text(Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
