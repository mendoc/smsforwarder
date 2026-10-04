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
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.util.SimResolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Écran de paramétrage : nom du téléphone, nom de chaque SIM et expéditeurs autorisés.
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
    private LinearLayout simsContainer;
    private final Map<Integer, EditText> simNames = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = Settings.with(this);

        deviceName = findViewById(R.id.device_name);
        allowedSenders = findViewById(R.id.allowed_senders);
        simsContainer = findViewById(R.id.sims_container);
        findViewById(R.id.save).setOnClickListener(v -> save());

        deviceName.setText(settings.getDeviceName());
        allowedSenders.setText(TextUtils.join("\n", settings.getAllowedSenders()));
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
        String name = deviceName.getText().toString().trim();
        if (!name.isEmpty()) settings.setDeviceName(name);

        List<String> senders = new ArrayList<>(Arrays.asList(allowedSenders.getText().toString().split("\\n")));
        settings.setAllowedSenders(senders);

        for (Map.Entry<Integer, EditText> entry : simNames.entrySet()) {
            settings.setSimName(entry.getKey(), entry.getValue().getText().toString());
        }
        allowedSenders.setText(TextUtils.join("\n", settings.getAllowedSenders()));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
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
