package com.dimitriongoua.smsforwarder.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.view.View;
import android.widget.EditText;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.UrlDestination;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.IOException;

/** Modifier la destination « Miango · SMS des SIM » (en-tête enregistré), comme sur la maquette. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h960dp-port-xhdpi")
public class DestinationEditScreenshotTest {
    private static Activity open(String id) {
        return Robolectric.buildActivity(DestinationEditActivity.class,
                DestinationEditActivity.intent(Screens.app(), id)).setup().get();
    }

    private static String text(Activity activity, int id) {
        return ((EditText) activity.findViewById(id)).getText().toString();
    }

    @Test
    public void modifierUneDestination() throws IOException {
        Activity activity = open(DestinationStore.SMS_INCOMING_ID);
        assertEquals("Miango · SMS des SIM", text(activity, R.id.edit_title_field));
        assertEquals("X-Sms-Token", text(activity, R.id.edit_header_name));
        assertEquals("", text(activity, R.id.edit_header_value));
        assertEquals(View.VISIBLE, activity.findViewById(R.id.edit_delete).getVisibility());
        Screens.capture(activity, "modifier-destination");
    }

    @Test
    public void leTitreEstEnregistreEtLaValeurDeLEnTeteConservee() {
        DestinationStore store = DestinationStore.with(Screens.app());
        String token = store.findUrl(DestinationStore.SMS_INCOMING_ID).getHeaderValue();
        Activity activity = open(DestinationStore.SMS_INCOMING_ID);
        ((EditText) activity.findViewById(R.id.edit_title_field)).setText("Inbox Miango");
        activity.findViewById(R.id.edit_save).performClick();
        UrlDestination saved = store.findUrl(DestinationStore.SMS_INCOMING_ID);
        assertEquals("Inbox Miango", saved.getTitle());
        assertEquals(token, saved.getHeaderValue());
        assertTrue(store.names().containsValue("Inbox Miango"));
        assertTrue(activity.isFinishing());
    }

    @Test
    public void nouvelleDestinationSansSupprimer() {
        Activity activity = open(null);
        assertEquals(View.GONE, activity.findViewById(R.id.edit_delete).getVisibility());
        assertEquals(View.GONE, activity.findViewById(R.id.edit_header_keep).getVisibility());
        ((EditText) activity.findViewById(R.id.edit_url_field)).setText("pas une url");
        activity.findViewById(R.id.edit_save).performClick();
        assertTrue(((EditText) activity.findViewById(R.id.edit_url_field)).getError() != null);
    }
}
