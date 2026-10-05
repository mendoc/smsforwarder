package com.dimitriongoua.smsforwarder.activity;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.permission.Permissions;

/**
 * Autorisations (maquette « Autorisations ») : progression « n/4 », rôle de chaque autorisation
 * et « Autoriser » sur celles qui manquent. S'ouvre au premier lancement et dès qu'une
 * autorisation système est retirée ; une autorisation bloquée (« Ne plus demander ») renvoie
 * vers les réglages de l'application, la batterie vers ceux d'Android.
 */
public class AutorisationsActivity extends AppCompatActivity {
    private static final int REQUEST = 10057;
    private static final String PREFS = "permissions";

    private static final int[][] TEXTS = {
            {R.string.permissions_receive_sms, R.string.permissions_receive_sms_text},
            {R.string.permissions_read_sms, R.string.permissions_read_sms_text},
            {R.string.permissions_phone, R.string.permissions_phone_text},
            {R.string.permissions_battery, R.string.permissions_battery_text},
    };

    /** Rationale avant la demande en cours : distingue un refus tout juste choisi d'un blocage ancien. */
    private boolean rationaleBefore;

    public static void open(Context context) {
        context.startActivity(new Intent(context, AutorisationsActivity.class));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autorisations);
        findViewById(R.id.perm_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        Permissions.Kind[] kinds = Permissions.Kind.values();
        LinearLayout list = findViewById(R.id.perm_list);
        list.removeAllViews();
        int granted = 0;
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < kinds.length; i++) {
            Permissions.Kind kind = kinds[i];
            boolean ok = Permissions.granted(this, kind);
            if (ok) granted++;
            View card = inflater.inflate(R.layout.item_permission, list, false);
            card.setBackgroundResource(ok ? R.drawable.bg_facts : R.drawable.bg_permission_missing);
            card.findViewById(R.id.permission_dot).setBackgroundResource(ok ? R.drawable.bg_status_pine : R.drawable.bg_status_red);
            ((ImageView) card.findViewById(R.id.permission_icon)).setImageResource(ok ? R.drawable.ic_check_bold : R.drawable.ic_close_bold);
            ((TextView) card.findViewById(R.id.permission_title)).setText(TEXTS[i][0]);
            ((TextView) card.findViewById(R.id.permission_text)).setText(TEXTS[i][1]);
            TextView state = card.findViewById(R.id.permission_state);
            state.setText(!ok ? R.string.permissions_denied
                    : kind == Permissions.Kind.BATTERY ? R.string.permissions_set : R.string.permissions_granted);
            state.setTextColor(ContextCompat.getColor(this, ok ? R.color.pine : R.color.red_ink));
            View allow = card.findViewById(R.id.permission_allow);
            allow.setVisibility(ok ? View.GONE : View.VISIBLE);
            allow.setOnClickListener(v -> allow(kind));
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) card.getLayoutParams();
            if (i > 0) params.topMargin = Math.round(10 * getResources().getDisplayMetrics().density);
            list.addView(card, params);
        }
        ((TextView) findViewById(R.id.perm_count)).setText(granted + "/" + kinds.length);
        ((com.dimitriongoua.smsforwarder.widget.ProgressRing) findViewById(R.id.perm_ring))
                .setProgress(granted / (float) kinds.length);
    }

    private void allow(Permissions.Kind kind) {
        if (kind == Permissions.Kind.BATTERY) {
            openBatterySettings();
            return;
        }
        String permission = Permissions.permissionOf(kind);
        rationaleBefore = ActivityCompat.shouldShowRequestPermissionRationale(this, permission);
        ActivityCompat.requestPermissions(this, new String[]{permission}, REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST || permissions.length == 0) return;
        String permission = permissions[0];
        boolean denied = grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED;
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean askedBefore = prefs.getBoolean(permission, false);
        prefs.edit().putBoolean(permission, true).apply();
        // Refus sans dialogue : déjà demandée, pas de rationale avant ni après → bloquée.
        if (denied && askedBefore && !rationaleBefore
                && !ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
            Toast.makeText(this, R.string.permissions_settings_hint, Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return;
        }
        render();
    }

    /** Demande directe d'exemption (application hors Play Store), sinon la liste d'Android. */
    @SuppressLint("BatteryLife")
    private void openBatterySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", getPackageName(), null)));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, R.string.permissions_battery_text, Toast.LENGTH_LONG).show();
            }
        }
    }
}
