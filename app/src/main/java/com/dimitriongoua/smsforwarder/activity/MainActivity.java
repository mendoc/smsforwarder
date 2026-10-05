package com.dimitriongoua.smsforwarder.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.BuildConfig;
import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.filter.InvalidRuleException;
import com.dimitriongoua.smsforwarder.filter.SimPolicy;
import com.dimitriongoua.smsforwarder.filter.SmsFilter;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.journal.JournalFormat;
import com.dimitriongoua.smsforwarder.send.Forwarder;
import com.dimitriongoua.smsforwarder.sync.SyncEngine;
import com.dimitriongoua.smsforwarder.sync.SyncScheduler;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.util.SimResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Écran de paramétrage : nom du téléphone, nom et transfert de chaque SIM, expéditeurs autorisés et
 * règles de filtrage avancées. Affiche aussi l'état de la synchronisation des SMS manqués.
 * La version installée est affichée en pied de page.
 */
public class MainActivity extends AppCompatActivity {
    private static final int MY_PERMISSIONS_REQUEST = 10055;
    private static final String[] PERMISSIONS = {
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_PHONE_STATE,
    };

    private Settings settings;
    private EditText deviceName;
    private EditText allowedSenders;
    private EditText filterRules;
    private LinearLayout simsContainer;
    private final Map<Integer, EditText> simNames = new HashMap<>();
    private TextView unknownSimNote;
    private final Handler main = new Handler(Looper.getMainLooper());
    // Lecture de l'état sans attendre les envois en cours sur Forwarder.EXECUTOR.
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();
    private TextView syncStatus;
    private Button syncNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = Settings.with(this);

        deviceName = findViewById(R.id.device_name);
        allowedSenders = findViewById(R.id.allowed_senders);
        filterRules = findViewById(R.id.filter_rules);
        simsContainer = findViewById(R.id.sims_container);
        findViewById(R.id.save).setOnClickListener(v -> save());
        findViewById(R.id.open_destinations).setOnClickListener(v ->
                startActivity(new Intent(this, DestinationsActivity.class)));
        findViewById(R.id.open_journal).setOnClickListener(v ->
                startActivity(new Intent(this, JournalActivity.class)));
        syncStatus = findViewById(R.id.sync_status);
        syncNow = findViewById(R.id.sync_now);
        syncNow.setOnClickListener(v -> syncNow());
        SyncScheduler.ensurePeriodic(this);

        deviceName.setText(settings.getDeviceName());
        allowedSenders.setText(TextUtils.join("\n", settings.getAllowedSenders()));
        filterRules.setText(TextUtils.join("\n", settings.getFilterRules()));
        renderSims();
        ((TextView) findViewById(R.id.app_version)).setText(
                getString(R.string.app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));

        if (!hasAllPermissions()) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, MY_PERMISSIONS_REQUEST);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderSyncStatus();
    }

    /** Dernière synchronisation réussie, dernière vérification et envois en attente. */
    private void renderSyncStatus() {
        READER.execute(() -> {
            int open = JournalDb.get(this).countOpen();
            main.post(() -> {
                if (isFinishing()) return;
                TimeZone zone = TimeZone.getDefault();
                List<String> lines = new ArrayList<>();
                if (settings.getLastSync() > 0) {
                    lines.add(getString(R.string.sync_last, JournalFormat.dateTime(settings.getLastSync(), zone)));
                } else {
                    lines.add(getString(R.string.sync_never));
                }
                if (settings.getLastCheck() > 0) {
                    lines.add(getString(R.string.sync_checked, JournalFormat.dateTime(settings.getLastCheck(), zone)));
                }
                if (open > 0) lines.add(getString(R.string.sync_open, open));
                syncStatus.setText(TextUtils.join("\n", lines));
            });
        });
    }

    private void syncNow() {
        syncNow.setEnabled(false);
        syncStatus.setText(R.string.sync_running);
        Forwarder.EXECUTOR.execute(() -> {
            String message;
            try {
                SyncEngine.Result result = SyncEngine.with(this).run();
                if (result.open > 0) SyncScheduler.scheduleRetry(this);
                message = getString(R.string.sync_done, result.recovered, result.open);
            } catch (RuntimeException e) {
                SyncScheduler.scheduleRetry(this);
                message = getString(R.string.sync_failed);
            }
            final String text = message;
            main.post(() -> {
                if (isFinishing()) return;
                syncNow.setEnabled(true);
                Toast.makeText(this, text, Toast.LENGTH_LONG).show();
                renderSyncStatus();
            });
        });
    }

    private boolean hasAllPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Par SIM active : nom (prérempli avec le nom enregistré) et case de transfert, appliquée
     * dès qu'elle est touchée. Les SIM désactivées absentes du téléphone restent listées,
     * pour pouvoir les réactiver.
     */
    private void renderSims() {
        simsContainer.removeAllViews();
        simNames.clear();
        SimPolicy policy = settings.getSimPolicy();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simsContainer.addView(help(getString(R.string.sims_permission)));
            addUnknownSimNote(policy);
            return;
        }
        List<SubscriptionInfo> sims = SimResolver.activeSims(this);
        if (sims.isEmpty()) simsContainer.addView(help(getString(R.string.sims_none)));
        Set<Integer> shown = new HashSet<>();
        for (SubscriptionInfo sim : sims) {
            int id = sim.getSubscriptionId();
            shown.add(id);
            CharSequence carrier = sim.getCarrierName();
            String slot = "SIM " + (sim.getSimSlotIndex() + 1) + (carrier == null ? "" : " · " + carrier);

            EditText field = new EditText(this);
            field.setHint(getString(R.string.sim_hint, slot));
            field.setSingleLine(true);
            String name = settings.getSimName(id);
            field.setText(name == null ? "" : name);
            simsContainer.addView(help(slot));
            simsContainer.addView(field);
            addForwardToggle(id, name == null ? slot : name, policy);
            simNames.put(id, field);
        }
        for (int id : policy.disabled()) {
            if (shown.contains(id)) continue;
            String name = settings.getSimName(id);
            String label = getString(R.string.sim_absent, name == null ? String.valueOf(id) : name);
            simsContainer.addView(help(label));
            addForwardToggle(id, label, policy);
        }
        addUnknownSimNote(policy);
    }

    private void addUnknownSimNote(SimPolicy policy) {
        unknownSimNote = help(getString(R.string.sims_unknown_blocked));
        simsContainer.addView(unknownSimNote);
        updateUnknownSimNote(policy);
    }

    private void updateUnknownSimNote(SimPolicy policy) {
        if (unknownSimNote != null) unknownSimNote.setVisibility(policy.anyDisabled() ? View.VISIBLE : View.GONE);
    }

    private void addForwardToggle(int id, String label, SimPolicy policy) {
        CheckBox box = new CheckBox(this);
        box.setText(R.string.sim_forward);
        box.setChecked(policy.isEnabled(id));
        TextView state = help("");
        showSimState(state, policy.stateOf(id));
        // Appliqué tout de suite (pas de bouton Enregistrer) ; les noms en cours de saisie
        // ne sont pas touchés.
        box.setOnCheckedChangeListener((view, checked) -> {
            settings.setSimEnabled(id, checked, System.currentTimeMillis());
            SimPolicy updated = settings.getSimPolicy();
            showSimState(state, updated.stateOf(id));
            updateUnknownSimNote(updated);
            Toast.makeText(this, getString(checked ? R.string.sim_enabled_toast : R.string.sim_disabled_toast, label),
                    Toast.LENGTH_SHORT).show();
        });
        simsContainer.addView(box);
        simsContainer.addView(state);
    }

    private void showSimState(TextView view, SimPolicy.State state) {
        String text = simState(state);
        view.setText(text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** « Désactivée le … » ou « Réactivée le … », vide pour une SIM jamais réglée. */
    private String simState(SimPolicy.State state) {
        if (state == null || state.changedAt <= 0) return "";
        String when = JournalFormat.dateTime(state.changedAt, TimeZone.getDefault());
        return getString(state.enabled ? R.string.sim_enabled_since : R.string.sim_disabled_since, when);
    }

    private TextView help(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setAlpha(0.7f);
        view.setPadding(0, 12, 0, 0);
        return view;
    }

    private void save() {
        // Une règle invalide bloque tout l'enregistrement : rien n'est sauvegardé à moitié.
        List<String> rules = lines(filterRules);
        List<InvalidRuleException> errors = SmsFilter.validate(rules);
        if (!errors.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (InvalidRuleException error : errors) messages.add(error.getMessage());
            String message = getString(R.string.rules_invalid, TextUtils.join("\n", messages));
            filterRules.setError(message);
            filterRules.requestFocus();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        filterRules.setError(null);
        settings.setFilterRules(rules);

        String name = deviceName.getText().toString().trim();
        if (!name.isEmpty()) settings.setDeviceName(name);

        settings.setAllowedSenders(lines(allowedSenders));

        for (Map.Entry<Integer, EditText> entry : simNames.entrySet()) {
            settings.setSimName(entry.getKey(), entry.getValue().getText().toString());
        }
        allowedSenders.setText(TextUtils.join("\n", settings.getAllowedSenders()));
        filterRules.setText(TextUtils.join("\n", settings.getFilterRules()));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    private static List<String> lines(EditText field) {
        return new ArrayList<>(Arrays.asList(field.getText().toString().split("\\n")));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_PERMISSIONS_REQUEST) {
            boolean smsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, smsGranted ? "Reception des SMS autorisée" : "Pas possible de recevoir des SMS", Toast.LENGTH_SHORT).show();
            renderSims();
        }
    }
}
