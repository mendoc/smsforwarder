package com.dimitriongoua.smsforwarder.activity;

import static com.dimitriongoua.smsforwarder.activity.Screens.MIN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.TelegramDestination;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.SendOutcome;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/** Destinations par défaut (Miango ×2) et Telegram, avec un envoi Telegram en attente. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h1080dp-port-xhdpi")
public class DestinationsScreenshotTest {
    private static final String HANDLER = "url:" + DestinationStore.SMS_HANDLER_ID;
    private static final String INCOMING = "url:" + DestinationStore.SMS_INCOMING_ID;
    private TimeZone previousZone;

    @Before
    public void seed() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(Screens.LIBREVILLE);
        JournalDb.resetForTests();
        DestinationStore.with(Screens.app()).setTelegram(new TelegramDestination("123456:ABCdef7Hk", "-1001234", true));
        JournalDb journal = JournalDb.get(Screens.app());
        long now = System.currentTimeMillis();
        SMS sms = new SMS();
        sms.setAddress("AirtelMoney");
        sms.setBody("Vous avez recu 25 000 FCFA.");
        sms.setTimestamp(String.valueOf(now - 4 * MIN));
        sms.setReceivedAt(now - 4 * MIN);
        sms.setSubscriptionId(Screens.SIM_AM6);
        Map<String, String> destinations = new LinkedHashMap<>();
        destinations.put(TelegramDestination.KEY, "Telegram");
        destinations.put(HANDLER, "miango.netlify.app/smshandler");
        destinations.put(INCOMING, "miango.netlify.app/sms/incoming");
        long id = journal.record(sms, "Am6", Delivery.VIA_RECEPTION, destinations);
        journal.recordAttempt(id, HANDLER, SendOutcome.http(200), now - 4 * MIN, Delivery.VIA_RECEPTION);
        journal.recordAttempt(id, INCOMING, SendOutcome.http(200), now - 4 * MIN, Delivery.VIA_RECEPTION);
        journal.recordAttempt(id, TelegramDestination.KEY, SendOutcome.noResponse("Délai dépassé"), now - 4 * MIN,
                Delivery.VIA_RECEPTION);
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
        TimeZone.setDefault(previousZone);
    }

    private static Activity open() {
        Activity activity = Robolectric.buildActivity(DestinationsActivity.class).setup().get();
        Screens.waitFor(() -> ((LinearLayout) activity.findViewById(R.id.dest_list)).getChildCount() == 3);
        return activity;
    }

    @Test
    public void destinations() throws IOException {
        Activity activity = open();
        LinearLayout cards = activity.findViewById(R.id.dest_list);
        assertEquals("Miango · reçus", text(cards, 0, R.id.dest_name));
        assertEquals("Miango · SMS des SIM", text(cards, 1, R.id.dest_name));
        assertEquals("X-Sms-Token : ••••••••", text(cards, 1, R.id.dest_header));
        assertEquals("Telegram", text(cards, 2, R.id.dest_name));
        assertEquals("Bot ••••7Hk · conversation -1001234", text(cards, 2, R.id.dest_url));
        assertEquals("1 envoi en attente, sans réponse", text(cards, 2, R.id.dest_status));
        Screens.capture(activity, "destinations");
    }

    @Test
    public void desactiverUneUrl() {
        Activity activity = open();
        LinearLayout cards = activity.findViewById(R.id.dest_list);
        Toggle toggle = cards.getChildAt(0).findViewById(R.id.dest_toggle);
        toggle.performClick();
        assertFalse(DestinationStore.with(Screens.app()).findUrl(DestinationStore.SMS_HANDLER_ID).isEnabled());
    }

    private static String text(LinearLayout cards, int index, int id) {
        return ((TextView) cards.getChildAt(index).findViewById(id)).getText().toString();
    }
}
