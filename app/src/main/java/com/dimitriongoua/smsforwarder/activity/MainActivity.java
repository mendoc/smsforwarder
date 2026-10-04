package com.dimitriongoua.smsforwarder.activity;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.telephony.SubscriptionInfo;
import android.text.TextUtils;
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
import com.dimitriongoua.smsforwarder.filter.SmsFilter;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.util.SimResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran de paramétrage : nom du téléphone, nom de chaque SIM, expéditeurs autorisés et
 * règles de filtrage avancées.
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

    private boolean hasAllPermissions() {
        for (String permission : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /** Un champ par SIM active, prérempli avec le nom enregistré ou l'opérateur. */
    private void renderSims() {
        simsContainer.removeAllViews();
        simNames.clear();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            simsContainer.addView(help(getString(R.string.sims_permission)));
            return;
        }
        List<SubscriptionInfo> sims = SimResolver.activeSims(this);
        if (sims.isEmpty()) {
            simsContainer.addView(help(getString(R.string.sims_none)));
            return;
        }
        for (SubscriptionInfo sim : sims) {
            int id = sim.getSubscriptionId();
            CharSequence carrier = sim.getCarrierName();
            String slot = "SIM " + (sim.getSimSlotIndex() + 1) + (carrier == null ? "" : " · " + carrier);

            EditText field = new EditText(this);
            field.setHint(getString(R.string.sim_hint, slot));
            field.setSingleLine(true);
            String name = settings.getSimName(id);
            field.setText(name == null ? "" : name);
            simsContainer.addView(help(slot));
            simsContainer.addView(field);
            simNames.put(id, field);
        }
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
