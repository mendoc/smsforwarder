package com.dimitriongoua.smsforwarder.activity;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.filter.SimPolicy;
import com.dimitriongoua.smsforwarder.journal.HomeFormat;
import com.dimitriongoua.smsforwarder.journal.HomeStats;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.util.SimResolver;
import com.dimitriongoua.smsforwarder.widget.Toggle;

import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carte SIM (maquette « Carte SIM ») : identité et chiffres du jour, transfert appliqué tout
 * de suite (règle {@link SimPolicy}), nom envoyé à Miango et règles de la désactivation.
 * Ouverte depuis l'Accueil ; fonctionne aussi pour une SIM désactivée absente du téléphone.
 */
public class SimActivity extends AppCompatActivity {
    public static final String EXTRA_SUBSCRIPTION_ID = "subscription_id";
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private Settings settings;
    private int subscriptionId;
    private String defaultName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sim);
        settings = Settings.with(this);
        subscriptionId = getIntent().getIntExtra(EXTRA_SUBSCRIPTION_ID, SimPolicy.UNKNOWN);
        findViewById(R.id.simd_back).setOnClickListener(v -> finish());

        int[] rules = {R.string.sim_rule_1, R.string.sim_rule_2, R.string.sim_rule_3};
        int[] ids = {R.id.simd_rule_1, R.id.simd_rule_2, R.id.simd_rule_3};
        for (int i = 0; i < ids.length; i++) {
            View rule = findViewById(ids[i]);
            ((TextView) rule.findViewById(R.id.rule_number)).setText(String.valueOf(i + 1));
            ((TextView) rule.findViewById(R.id.rule_text)).setText(rules[i]);
        }

        bindIdentity();
        EditText name = findViewById(R.id.simd_name_field);
        String saved = settings.getSimName(subscriptionId);
        name.setText(saved == null ? "" : saved);
        name.setHint(defaultName);
        findViewById(R.id.simd_save).setOnClickListener(v -> {
            settings.setSimName(subscriptionId, name.getText().toString());
            bindIdentity();
            Toast.makeText(this, R.string.sim_saved, Toast.LENGTH_SHORT).show();
        });

        Toggle toggle = findViewById(R.id.simd_toggle);
        toggle.setChecked(settings.getSimPolicy().isEnabled(subscriptionId));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            settings.setSimEnabled(subscriptionId, checked, System.currentTimeMillis());
            bindNote();
            Toast.makeText(this, getString(checked ? R.string.sim_enabled_toast : R.string.sim_disabled_toast,
                    displayName()), Toast.LENGTH_SHORT).show();
        });
        bindNote();
    }

    @Override
    protected void onResume() {
        super.onResume();
        final long now = System.currentTimeMillis();
        final TimeZone zone = TimeZone.getDefault();
        READER.execute(() -> {
            HomeStats stats = JournalDb.get(this).homeStats(HomeFormat.startOfDay(now, zone));
            main.post(() -> {
                if (isFinishing()) return;
                Integer today = stats.relayedTodayBySim.get(subscriptionId);
                Long last = stats.lastReceivedBySim.get(subscriptionId);
                ((TextView) findViewById(R.id.simd_today)).setText(String.valueOf(today == null ? 0 : today));
                ((TextView) findViewById(R.id.simd_last)).setText(
                        HomeFormat.syncTime(last == null ? 0 : last, now, zone));
            });
        });
    }

    @SuppressWarnings("deprecation") // SubscriptionInfo.getNumber, comme SimResolver
    private void bindIdentity() {
        SubscriptionInfo info = null;
        for (SubscriptionInfo sim : SimResolver.activeSims(this)) {
            if (sim.getSubscriptionId() == subscriptionId) info = sim;
        }
        TextView slot = findViewById(R.id.simd_slot);
        TextView sub = findViewById(R.id.simd_sub);
        if (info != null) {
            int n = info.getSimSlotIndex() + 1;
            slot.setText(getString(R.string.sim_slot, n));
            CharSequence carrier = info.getCarrierName();
            String number = info.getNumber() == null || info.getNumber().trim().isEmpty() ? null : info.getNumber().trim();
            String text = carrier == null ? "" : carrier.toString();
            if (number != null) text = text.isEmpty() ? number : text + " · " + number;
            sub.setText(text);
            sub.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
            defaultName = carrier == null ? "SIM " + n : carrier.toString();
        } else {
            slot.setText(R.string.sim_slot_absent);
            sub.setText(R.string.home_sim_absent);
            defaultName = getString(R.string.home_sim_absent);
        }
        ((TextView) findViewById(R.id.simd_name)).setText(displayName());
    }

    private String displayName() {
        String name = settings.getSimName(subscriptionId);
        return name == null ? defaultName : name;
    }

    private void bindNote() {
        SimPolicy.State state = settings.getSimPolicy().stateOf(subscriptionId);
        String text = state == null ? "" : HomeFormat.simChange(state.enabled, state.changedAt, TimeZone.getDefault());
        TextView note = findViewById(R.id.simd_note);
        note.setText(text);
        note.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
