package com.dimitriongoua.smsforwarder.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.provider.Settings;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.JournalDb;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;

/** Autorisations avec « Téléphone » refusée (3/4), comme sur la maquette. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h844dp-port-xhdpi")
public class AutorisationsScreenshotTest {
    @Before
    public void seed() {
        JournalDb.resetForTests();
        shadowOf(Screens.app()).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS);
        Screens.batteryUnrestricted();
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
    }

    @Test
    public void autorisations() throws IOException {
        Activity activity = Robolectric.buildActivity(AutorisationsActivity.class).setup().get();
        assertEquals("3/4", ((TextView) activity.findViewById(R.id.perm_count)).getText().toString());
        LinearLayout list = activity.findViewById(R.id.perm_list);
        assertEquals(4, list.getChildCount());
        assertEquals("Accordée", state(list, 0));
        assertEquals("Refusée", state(list, 2));
        assertEquals("Réglée", state(list, 3));
        assertEquals(View.VISIBLE, list.getChildAt(2).findViewById(R.id.permission_allow).getVisibility());
        assertEquals(View.GONE, list.getChildAt(0).findViewById(R.id.permission_allow).getVisibility());
        Screens.capture(activity, "autorisations");
    }

    @Test
    public void autoriserDemandeLaPermissionTelephone() {
        Activity activity = Robolectric.buildActivity(AutorisationsActivity.class).setup().get();
        LinearLayout list = activity.findViewById(R.id.perm_list);
        list.getChildAt(2).findViewById(R.id.permission_allow).performClick();
        String[] requested = shadowOf(activity).getLastRequestedPermission().requestedPermissions;
        assertEquals(Manifest.permission.READ_PHONE_STATE, requested[0]);
    }

    @Test
    public void laBatterieOuvreLaDemandeDExemption() {
        shadowOf(Screens.app().getSystemService(android.os.PowerManager.class))
                .setIgnoringBatteryOptimizations(Screens.app().getPackageName(), false);
        Activity activity = Robolectric.buildActivity(AutorisationsActivity.class).setup().get();
        LinearLayout list = activity.findViewById(R.id.perm_list);
        list.getChildAt(3).findViewById(R.id.permission_allow).performClick();
        Intent intent = shadowOf(activity).getNextStartedActivity();
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.getAction());
        assertTrue(intent.getData().toString().endsWith(Screens.app().getPackageName()));
    }

    @Test
    public void lAccueilOuvreLesAutorisationsSiUneManque() {
        MainActivity.permissionsShown = false;
        Activity home = Robolectric.buildActivity(MainActivity.class).setup().get();
        Intent intent = shadowOf(home).getNextStartedActivity();
        assertEquals(AutorisationsActivity.class.getName(), intent.getComponent().getClassName());
    }

    private static String state(LinearLayout list, int index) {
        return ((TextView) list.getChildAt(index).findViewById(R.id.permission_state)).getText().toString();
    }
}
