package com.dimitriongoua.smsforwarder.activity;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.dimitriongoua.smsforwarder.BuildConfig;
import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.filter.InvalidRuleException;
import com.dimitriongoua.smsforwarder.filter.SmsFilter;
import com.dimitriongoua.smsforwarder.journal.HomeFormat;
import com.dimitriongoua.smsforwarder.permission.Permissions;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Réglages (maquette « Réglages ») : nom et identifiant du téléphone, expéditeurs autorisés
 * en étiquettes, règles avancées, autorisations et version. Enregistré par le bouton
 * « Enregistrer » ; une règle invalide bloque tout l'enregistrement.
 */
public class ReglagesActivity extends AppCompatActivity {
    private Settings settings;
    private EditText deviceName;
    private EditText rules;
    private ChipGroup sendersGroup;
    private final List<String> senders = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reglages);
        settings = Settings.with(this);
        TabBar.bind(this, TabBar.Tab.SETTINGS);

        deviceName = findViewById(R.id.settings_device_name);
        deviceName.setText(settings.getDeviceName());
        ((TextView) findViewById(R.id.settings_device_id)).setText(HomeFormat.shortId(settings.getDeviceId()));

        sendersGroup = findViewById(R.id.settings_senders);
        senders.addAll(settings.getAllowedSenders());
        renderSenders();

        rules = findViewById(R.id.settings_rules);
        rules.setText(TextUtils.join("\n", settings.getFilterRules()));
        rules.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                renderRulesCount();
            }
        });
        renderRulesCount();

        ((TextView) findViewById(R.id.settings_version)).setText(
                getString(R.string.settings_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
        findViewById(R.id.settings_alert).setOnClickListener(v -> AutorisationsActivity.open(this));
        findViewById(R.id.settings_permissions_row).setOnClickListener(v -> AutorisationsActivity.open(this));
        findViewById(R.id.settings_save).setOnClickListener(v -> save());
    }

    @Override
    protected void onResume() {
        super.onResume();
        renderPermissions();
    }

    private void renderPermissions() {
        List<Permissions.Kind> absent = Permissions.missing(this);
        int missing = absent.size();
        boolean smsMissing = absent.contains(Permissions.Kind.RECEIVE_SMS) || absent.contains(Permissions.Kind.READ_SMS);
        boolean phoneMissing = absent.contains(Permissions.Kind.PHONE);
        findViewById(R.id.settings_alert).setVisibility(missing > 0 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.settings_alert_title)).setText(HomeFormat.missingPermissions(missing));
        ((TextView) findViewById(R.id.settings_alert_sub)).setText(
                smsMissing ? R.string.settings_alert_sub_sms
                        : phoneMissing ? R.string.settings_alert_sub : R.string.settings_alert_sub_battery);
        TextView state = findViewById(R.id.settings_permissions_state);
        state.setText(missing > 0 ? getString(R.string.settings_permissions_missing, missing)
                : getString(R.string.settings_permissions_ok));
        state.setTextColor(ContextCompat.getColor(this, missing > 0 ? R.color.red_ink : R.color.pine));
    }

    private void renderRulesCount() {
        int count = 0;
        for (String line : lines(rules)) {
            if (!line.trim().isEmpty()) count++;
        }
        ((TextView) findViewById(R.id.settings_rules_count)).setText(HomeFormat.rules(count));
    }

    /** Une étiquette par expéditeur (toucher pour la retirer), puis « + Ajouter ». */
    private void renderSenders() {
        sendersGroup.removeAllViews();
        for (String sender : senders) {
            TextView chip = chip(sender, true);
            chip.setOnClickListener(v -> new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.settings_sender_remove, sender))
                    .setPositiveButton(R.string.settings_sender_remove_ok, (d, w) -> {
                        senders.remove(sender);
                        renderSenders();
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show());
            sendersGroup.addView(chip);
        }
        TextView add = chip(getString(R.string.settings_sender_add), false);
        add.setOnClickListener(v -> addSender());
        sendersGroup.addView(add);
    }

    private TextView chip(String text, boolean sender) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTypeface(ResourcesCompat.getFont(this, sender ? R.font.plex_mono_regular : R.font.plex_sans_semibold));
        view.setTextColor(ContextCompat.getColor(this, sender ? R.color.pine : R.color.ink));
        view.setBackgroundResource(sender ? R.drawable.bg_chip_mono : R.drawable.bg_chip_dashed);
        view.setGravity(Gravity.CENTER);
        view.setIncludeFontPadding(false);
        view.setMinHeight(dp(32));
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        view.setClickable(true);
        view.setFocusable(true);
        return view;
    }

    private void addSender() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.senders_hint);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.settings_sender_add_title)
                .setView(box)
                .setPositiveButton(R.string.settings_add, (d, w) -> {
                    String sender = input.getText().toString().trim();
                    if (!sender.isEmpty() && !senders.contains(sender)) {
                        senders.add(sender);
                        renderSenders();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void save() {
        // Une règle invalide bloque tout l'enregistrement : rien n'est sauvegardé à moitié.
        List<String> ruleLines = lines(rules);
        List<InvalidRuleException> errors = SmsFilter.validate(ruleLines);
        if (!errors.isEmpty()) {
            List<String> messages = new ArrayList<>();
            for (InvalidRuleException error : errors) messages.add(error.getMessage());
            String message = getString(R.string.rules_invalid, TextUtils.join("\n", messages));
            rules.setError(message);
            rules.requestFocus();
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        rules.setError(null);
        settings.setFilterRules(ruleLines);
        String name = deviceName.getText().toString().trim();
        if (!name.isEmpty()) settings.setDeviceName(name);
        settings.setAllowedSenders(new ArrayList<>(senders));
        senders.clear();
        senders.addAll(settings.getAllowedSenders());
        renderSenders();
        rules.setText(TextUtils.join("\n", settings.getFilterRules()));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    private static List<String> lines(EditText field) {
        return new ArrayList<>(Arrays.asList(field.getText().toString().split("\\n")));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
