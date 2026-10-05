package com.dimitriongoua.smsforwarder.activity;

import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.util.DisplayMetrics;
import android.view.View;

import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.SendOutcome;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowSubscriptionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.BooleanSupplier;

/**
 * Données d'exemple et captures des tests d'écran (Robolectric, rendu natif) : les PNG vont
 * dans {@code app/build/screenshots/}, publiés par le CI pour les comparer à la maquette.
 */
final class Screens {
    static final TimeZone LIBREVILLE = TimeZone.getTimeZone("Africa/Libreville");
    /** SIM de la maquette : « Am6 » (Airtel, emplacement 1) et « Moov · perso » (emplacement 2). */
    static final int SIM_AM6 = 3;
    static final int SIM_MOOV = 4;
    static final long MIN = 60 * 1000L;

    private Screens() {
    }

    static Application app() {
        return RuntimeEnvironment.getApplication();
    }

    static void grantAll() {
        shadowOf(app()).grantPermissions(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS,
                Manifest.permission.READ_PHONE_STATE);
    }

    /** Deux SIM actives, comme sur la maquette. */
    static void twoSims() {
        SubscriptionManager manager = app().getSystemService(SubscriptionManager.class);
        shadowOf(manager).setActiveSubscriptionInfos(
                ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(SIM_AM6).setSimSlotIndex(0)
                        .setCarrierName("Airtel").setNumber("+241 07 •• •• 12").buildSubscriptionInfo(),
                ShadowSubscriptionManager.SubscriptionInfoBuilder.newBuilder().setId(SIM_MOOV).setSimSlotIndex(1)
                        .setCarrierName("Moov").setNumber("+241 06 •• •• 48").buildSubscriptionInfo());
    }

    /** Heure locale : {@code daysAgo} jours avant aujourd'hui, à hh:mm. */
    static long at(int daysAgo, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(LIBREVILLE);
        calendar.add(Calendar.DAY_OF_YEAR, -daysAgo);
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    static long record(JournalDb journal, String sender, String body, long receivedAt, String source, int sim) {
        SMS sms = new SMS();
        sms.setAddress(sender);
        sms.setBody(body);
        sms.setTimestamp(String.valueOf(receivedAt));
        sms.setReceivedAt(receivedAt);
        sms.setSubscriptionId(sim);
        sms.setSimSlot(sim == SIM_MOOV ? 1 : 0);
        sms.setSimCarrier(sim == SIM_MOOV ? "Moov" : "Airtel");
        Map<String, String> destinations = new LinkedHashMap<>();
        destinations.put("miango", "Miango");
        destinations.put("sims", "SMS des SIM");
        destinations.put("telegram", "Telegram");
        return journal.record(sms, sim == SIM_MOOV ? "Moov · perso" : "Am6", source, destinations);
    }

    static void attempt(JournalDb journal, long id, String key, SendOutcome outcome) {
        journal.recordAttempt(id, key, outcome, System.currentTimeMillis(), Delivery.VIA_RECEPTION);
    }

    static void allSent(JournalDb journal, long id) {
        for (String key : new String[]{"miango", "sims", "telegram"}) attempt(journal, id, key, SendOutcome.http(200));
    }

    /** Laisse le fil de lecture et le fil principal travailler jusqu'à la condition. */
    static void waitFor(BooleanSupplier condition) {
        for (int i = 0; i < 300; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            if (condition.getAsBoolean()) break;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        shadowOf(Looper.getMainLooper()).idle();
    }

    static void capture(Activity activity, String name) throws IOException {
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
