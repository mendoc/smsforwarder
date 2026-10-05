package com.dimitriongoua.smsforwarder.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.google.android.material.chip.ChipGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;
import java.util.Arrays;

/** Réglages avec l'autorisation « Téléphone » manquante, comme sur la maquette. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h1080dp-port-xhdpi")
public class ReglagesScreenshotTest {
    @Before
    public void seed() {
        JournalDb.resetForTests();
        shadowOf(Screens.app()).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS);
        Settings.with(Screens.app()).setDeviceName("Galaxy A14 · Bureau");
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
    }

    @Test
    public void reglages() throws IOException {
        Activity activity = Robolectric.buildActivity(ReglagesActivity.class).setup().get();
        Screens.waitFor(() -> true);
        assertEquals(View.VISIBLE, activity.findViewById(R.id.settings_alert).getVisibility());
        assertEquals("1 autorisation manquante", text(activity, R.id.settings_alert_title));
        assertEquals("1 à corriger", text(activity, R.id.settings_permissions_state));
        assertEquals("1 règle", text(activity, R.id.settings_rules_count));
        // Expéditeurs par défaut + « + Ajouter »
        ChipGroup senders = activity.findViewById(R.id.settings_senders);
        assertEquals(Settings.with(Screens.app()).getAllowedSenders().size() + 1, senders.getChildCount());
        Screens.capture(activity, "reglages");
    }

    @Test
    public void enregistrerLesReglages() {
        Activity activity = Robolectric.buildActivity(ReglagesActivity.class).setup().get();
        ((EditText) activity.findViewById(R.id.settings_device_name)).setText("Téléphone du bureau");
        ((EditText) activity.findViewById(R.id.settings_rules)).setText("body:(?i)paypal\nfrom:^38643$");
        activity.findViewById(R.id.settings_save).performClick();
        Settings settings = Settings.with(Screens.app());
        assertEquals("Téléphone du bureau", settings.getDeviceName());
        assertEquals(Arrays.asList("body:(?i)paypal", "from:^38643$"), settings.getFilterRules());
        assertEquals("2 règles", text(activity, R.id.settings_rules_count));
    }

    @Test
    public void uneRegleInvalideBloqueLEnregistrement() {
        Activity activity = Robolectric.buildActivity(ReglagesActivity.class).setup().get();
        ((EditText) activity.findViewById(R.id.settings_device_name)).setText("Autre nom");
        EditText rules = activity.findViewById(R.id.settings_rules);
        rules.setText("body:([");
        activity.findViewById(R.id.settings_save).performClick();
        assertTrue(rules.getError() != null);
        assertEquals("Galaxy A14 · Bureau", Settings.with(Screens.app()).getDeviceName());
    }

    private static String text(Activity activity, int id) {
        return ((TextView) activity.findViewById(id)).getText().toString();
    }
}
