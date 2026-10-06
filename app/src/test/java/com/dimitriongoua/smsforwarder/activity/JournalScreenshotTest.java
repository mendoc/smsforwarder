package com.dimitriongoua.smsforwarder.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.SendOutcome;
import com.google.android.material.chip.ChipGroup;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * Écran Journal rendu sur la JVM (Robolectric, rendu graphique natif) avec un journal
 * d'exemple : vérifie les filtres et enregistre une capture PNG par état dans
 * {@code app/build/screenshots/} (liste, filtre En attente), publiée par le CI pour la comparer à la maquette.
 */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 33, qualifiers = "w390dp-h1160dp-port-xhdpi")
public class JournalScreenshotTest {
    private static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    private TimeZone previousZone;

    @Before
    public void seed() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(LIBREVILLE);
        JournalDb.resetForTests();
        JournalDb journal = JournalDb.get(RuntimeEnvironment.getApplication());

        long a = record(journal, "AirtelMoney", "Vous avez recu 25 000 FCFA du 07 •• •• 31. Nouveau solde : 437 350 FCFA. TID: PP2510…",
                at(0, 14, 26), Delivery.VIA_RECEPTION);
        attempt(journal, a, Screens.MIANGO, SendOutcome.http(200));
        attempt(journal, a, Screens.SIMS, SendOutcome.http(200));
        attempt(journal, a, Screens.TELEGRAM, SendOutcome.noResponse("Délai dépassé"));
        attempt(journal, a, Screens.TELEGRAM, SendOutcome.noResponse("Délai dépassé"));

        long b = record(journal, "AirtelMoney", "Votre solde est 412 350 FCFA.", at(0, 13, 58), Delivery.VIA_RECEPTION);
        allSent(journal, b);
        long c = record(journal, "AirtelMoney", "ENVOI reussi de 50 000 FCFA au 07 •• •• 90. Frais : 0 FCFA. TID: CI2510…",
                at(0, 11, 4), Delivery.VIA_SYNC);
        allSent(journal, c);
        long d = record(journal, "Paypal", "PayPal : votre code de sécurité est ••••••. Il expire dans 10 minutes.",
                at(1, 21, 47), Delivery.VIA_RECEPTION);
        attempt(journal, d, Screens.TELEGRAM, SendOutcome.http(200));
        attempt(journal, d, Screens.MIANGO, SendOutcome.http(400));
        attempt(journal, d, Screens.SIMS, SendOutcome.http(200));
        long e = record(journal, "38643", "Vous avez envoye 15 000 FCFA au 07 •• •• 55. TID:PP2510…", at(1, 18, 12), Delivery.VIA_RECEPTION);
        allSent(journal, e);
    }

    @After
    public void restore() {
        JournalDb.resetForTests();
        TimeZone.setDefault(previousZone);
    }

    @Test
    public void journalComplet() throws IOException {
        Activity activity = Robolectric.buildActivity(JournalActivity.class).setup().get();
        ListView list = waitForRows(activity, 5);
        assertEquals(5, list.getAdapter().getCount());
        capture(activity, "journal");
    }

    @Test
    public void filtreEnAttente() throws IOException {
        Activity activity = Robolectric.buildActivity(JournalActivity.class).setup().get();
        waitForRows(activity, 5);
        ChipGroup filters = activity.findViewById(R.id.journal_filters);
        filters.getChildAt(1).performClick();
        ListView list = waitForRows(activity, 1);
        assertEquals(1, list.getAdapter().getCount());
        capture(activity, "journal-en-attente");
    }

    @Test
    public void toucherUnSmsOuvreSonDetail() {
        Activity activity = Robolectric.buildActivity(JournalActivity.class).setup().get();
        ListView list = waitForRows(activity, 5);
        list.performItemClick(null, 0, list.getAdapter().getItemId(0));
        android.content.Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals(DetailActivity.class.getName(), next.getComponent().getClassName());
        assertEquals(list.getAdapter().getItemId(0), next.getLongExtra(DetailActivity.EXTRA_ID, -1));
    }

    @Test
    public void filtreEchecs() {
        Activity activity = Robolectric.buildActivity(JournalActivity.class).setup().get();
        waitForRows(activity, 5);
        ChipGroup filters = activity.findViewById(R.id.journal_filters);
        filters.getChildAt(2).performClick();
        ListView list = waitForRows(activity, 1);
        TextView sender = list.getAdapter().getView(0, null, list).findViewById(R.id.journal_sender);
        assertEquals("Paypal", sender.getText().toString());
    }

    /** Heure locale : {@code daysAgo} jours avant aujourd'hui, à hh:mm. */
    private static long at(int daysAgo, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(LIBREVILLE);
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long record(JournalDb journal, String sender, String body, long receivedAt, String source) {
        SMS sms = new SMS();
        sms.setAddress(sender);
        sms.setBody(body);
        sms.setTimestamp(String.valueOf(receivedAt));
        sms.setReceivedAt(receivedAt);
        sms.setSubscriptionId(3);
        sms.setSimSlot(0);
        sms.setSimCarrier("Airtel");
        Map<String, String> destinations = new LinkedHashMap<>();
        destinations.put(Screens.MIANGO, "https://miango.netlify.app/smshandler");
        destinations.put(Screens.SIMS, "https://miango.netlify.app/sms/incoming");
        destinations.put(Screens.TELEGRAM, "Telegram");
        return journal.record(sms, "Am6", source, destinations);
    }

    private static void attempt(JournalDb journal, long id, String key, SendOutcome outcome) {
        journal.recordAttempt(id, key, outcome, System.currentTimeMillis(), Delivery.VIA_RECEPTION);
    }

    private static void allSent(JournalDb journal, long id) {
        for (String key : new String[]{Screens.MIANGO, Screens.SIMS, Screens.TELEGRAM}) attempt(journal, id, key, SendOutcome.http(200));
    }

    /** Laisse le fil de lecture et le fil principal travailler jusqu'à {@code rows} lignes. */
    private static ListView waitForRows(Activity activity, int rows) {
        ListView list = activity.findViewById(R.id.journal_list);
        for (int i = 0; i < 200; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (list.getAdapter().getCount() == rows) break;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
        return list;
    }

    private static void capture(Activity activity, String name) throws IOException {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        View root = activity.getWindow().getDecorView();
        root.measure(View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, metrics.widthPixels, metrics.heightPixels);
        Bitmap bitmap = Bitmap.createBitmap(metrics.widthPixels, metrics.heightPixels, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(bitmap));
        File dir = new File("build/screenshots");
        assertTrue(dir.isDirectory() || dir.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
    }
}
