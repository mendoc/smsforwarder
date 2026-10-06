package com.dimitriongoua.smsforwarder.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.destination.DestinationFormat;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.UrlDestination;
import com.dimitriongoua.smsforwarder.filter.SimPolicy;
import com.dimitriongoua.smsforwarder.journal.HomeFormat;
import com.dimitriongoua.smsforwarder.journal.HomeStats;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.permission.Permissions;
import com.dimitriongoua.smsforwarder.send.Forwarder;
import com.dimitriongoua.smsforwarder.sync.SyncEngine;
import com.dimitriongoua.smsforwarder.sync.SyncScheduler;
import com.dimitriongoua.smsforwarder.util.Settings;
import com.dimitriongoua.smsforwarder.util.SimResolver;
import com.dimitriongoua.smsforwarder.widget.Toggle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accueil (maquette « Accueil ») : le relais fonctionne-t-il ? État et chiffres du jour,
 * envois en attente, SIM transférées (interrupteur appliqué tout de suite, détail dans la
 * Carte SIM) et synchronisation des SMS manqués. Les réglages sont dans {@link ReglagesActivity}.
 */
public class MainActivity extends AppCompatActivity {
    // Écran Autorisations ouvert une fois par processus (un retrait d'autorisation le relance).
    static boolean permissionsShown;
    // Lecture de l'état sans attendre les envois en cours sur Forwarder.EXECUTOR.
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private Settings settings;
    private View syncNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        settings = Settings.with(this);
        TabBar.bind(this, TabBar.Tab.HOME);
        findViewById(R.id.home_settings).setOnClickListener(v ->
                TabBar.open(this, TabBar.Tab.HOME, TabBar.Tab.SETTINGS));
        findViewById(R.id.home_sims_allow).setOnClickListener(v -> AutorisationsActivity.open(this));
        syncNow = findViewById(R.id.home_sync_now);
        syncNow.setOnClickListener(v -> syncNow());
        SyncScheduler.ensurePeriodic(this);
        // Premier lancement ou autorisation retirée (le système relance alors le processus).
        if (!permissionsShown && Permissions.runtimeMissing(this)) {
            permissionsShown = true;
            AutorisationsActivity.open(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    /** Lit le journal sur un fil à part, puis met tout l'écran à jour. */
    private void render() {
        final long now = System.currentTimeMillis();
        final TimeZone zone = TimeZone.getDefault();
        READER.execute(() -> {
            HomeStats stats = JournalDb.get(this).homeStats(HomeFormat.startOfDay(now, zone));
            main.post(() -> {
                if (isFinishing()) return;
                bind(stats, now, zone);
            });
        });
    }

    private void bind(HomeStats stats, long now, TimeZone zone) {
        ((TextView) findViewById(R.id.home_device)).setText(settings.getDeviceName());

        boolean receiving = granted(Manifest.permission.RECEIVE_SMS);
        boolean destinations = hasActiveDestination();
        boolean live = receiving && destinations;
        findViewById(R.id.home_live_dot).setBackgroundResource(live ? R.drawable.bg_live_dot : R.drawable.bg_live_dot_off);
        TextView label = findViewById(R.id.home_live_label);
        label.setText(live ? R.string.home_live_on : R.string.home_live_off);
        label.setTextColor(ContextCompat.getColor(this, live ? R.color.mint : R.color.hero_amber));
        ((TextView) findViewById(R.id.home_headline)).setText(live ? R.string.home_headline_on
                : !receiving ? R.string.home_headline_no_permission : R.string.home_headline_no_destination);
        ((TextView) findViewById(R.id.home_last)).setText(HomeFormat.lastRelayed(stats.lastRelayed, now, zone));

        ((TextView) findViewById(R.id.home_stat_relayed)).setText(String.valueOf(stats.relayedToday));
        TextView pending = findViewById(R.id.home_stat_pending);
        pending.setText(String.valueOf(stats.open));
        pending.setTextColor(ContextCompat.getColor(this, stats.open > 0 ? R.color.hero_amber : R.color.white));
        ((TextView) findViewById(R.id.home_stat_failed)).setText(String.valueOf(stats.failedToday));
        ((TextView) findViewById(R.id.home_stat_failed_label)).setText(stats.failedToday > 1
                ? R.string.home_stat_failed_plural : R.string.home_stat_failed);

        findViewById(R.id.home_alert).setVisibility(stats.open > 0 ? View.VISIBLE : View.GONE);
        // Un seul SMS en attente : son Détail ; plusieurs : le Journal filtré « En attente ».
        findViewById(R.id.home_alert).setOnClickListener(v -> startActivity(stats.openSms == 1
                ? new Intent(this, DetailActivity.class).putExtra(DetailActivity.EXTRA_ID, stats.openSmsId)
                : new Intent(this, JournalActivity.class).putExtra(JournalActivity.EXTRA_PENDING, true)));
        Map<String, String> names = DestinationStore.with(this).names();
        List<String> open = new ArrayList<>();
        for (int i = 0; i < stats.openDestinationKeys.size(); i++) {
            open.add(DestinationFormat.label(names, stats.openDestinationKeys.get(i), stats.openDestinations.get(i)));
        }
        ((TextView) findViewById(R.id.home_alert_title)).setText(HomeFormat.pending(stats.open, open));

        renderSims(stats, zone);

        TextView badge = findViewById(R.id.home_sync_badge);
        badge.setText(stats.open > 0 ? getString(R.string.home_sync_open, stats.open) : getString(R.string.home_sync_ok));
        badge.setBackgroundResource(stats.open > 0 ? R.drawable.bg_pill_amber : R.drawable.bg_pill_pine);
        badge.setTextColor(ContextCompat.getColor(this, stats.open > 0 ? R.color.amber_ink : R.color.pine));
        ((TextView) findViewById(R.id.home_sync_last)).setText(HomeFormat.syncTime(settings.getLastSync(), now, zone));
        ((TextView) findViewById(R.id.home_sync_checked)).setText(HomeFormat.syncTime(settings.getLastCheck(), now, zone));
    }

    private boolean hasActiveDestination() {
        DestinationStore store = DestinationStore.with(this);
        if (store.getTelegram().isActive()) return true;
        for (UrlDestination url : store.getUrls()) {
            if (url.isEnabled()) return true;
        }
        return false;
    }

    /** Une carte par SIM active, puis les SIM désactivées absentes du téléphone (pour les réactiver). */
    @SuppressWarnings("deprecation") // SubscriptionInfo.getNumber, comme SimResolver
    private void renderSims(HomeStats stats, TimeZone zone) {
        LinearLayout container = findViewById(R.id.home_sims);
        container.removeAllViews();
        SimPolicy policy = settings.getSimPolicy();
        boolean canRead = granted(Manifest.permission.READ_PHONE_STATE);
        findViewById(R.id.home_sims_permission).setVisibility(canRead ? View.GONE : View.VISIBLE);
        findViewById(R.id.home_sims_note).setVisibility(policy.anyDisabled() ? View.VISIBLE : View.GONE);
        TextView count = findViewById(R.id.home_sims_count);
        if (!canRead) {
            count.setText("");
            return;
        }
        List<Integer> shown = new ArrayList<>();
        int enabled = 0;
        for (SubscriptionInfo sim : SimResolver.activeSims(this)) {
            int id = sim.getSubscriptionId();
            shown.add(id);
            if (policy.isEnabled(id)) enabled++;
            CharSequence carrier = sim.getCarrierName();
            String number = sim.getNumber() == null || sim.getNumber().trim().isEmpty() ? null : sim.getNumber().trim();
            String sub = carrier == null ? "" : carrier.toString();
            if (number != null) sub = sub.isEmpty() ? number : sub + " · " + number;
            String name = settings.getSimName(id);
            if (name == null) name = carrier == null ? "SIM " + (sim.getSimSlotIndex() + 1) : carrier.toString();
            addSim(container, id, "SIM " + (sim.getSimSlotIndex() + 1), name, sub, policy, stats, zone);
        }
        for (int id : policy.disabled()) {
            if (shown.contains(id)) continue;
            shown.add(id);
            String name = settings.getSimName(id);
            addSim(container, id, "SIM", name == null ? getString(R.string.home_sim_absent) : name,
                    getString(R.string.home_sim_absent), policy, stats, zone);
        }
        count.setText(shown.isEmpty() ? getString(R.string.sims_none) : HomeFormat.simsForwarded(enabled, shown.size()));
    }

    private void addSim(LinearLayout container, int id, String badgeText, String name, String sub, SimPolicy policy,
                        HomeStats stats, TimeZone zone) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_home_sim, container, false);
        boolean on = policy.isEnabled(id);
        TextView badge = card.findViewById(R.id.sim_badge);
        badge.setText(badgeText);
        badge.setBackgroundResource(on ? R.drawable.bg_sim_badge_on : R.drawable.bg_sim_badge_off);
        badge.setTextColor(ContextCompat.getColor(this, on ? R.color.pine : R.color.ink_2));
        ((TextView) card.findViewById(R.id.sim_name)).setText(name);
        TextView subView = card.findViewById(R.id.sim_sub);
        subView.setText(sub);
        subView.setVisibility(sub.isEmpty() ? View.GONE : View.VISIBLE);

        ImageView icon = card.findViewById(R.id.sim_status_icon);
        TextView status = card.findViewById(R.id.sim_status);
        int color = ContextCompat.getColor(this, on ? R.color.pine : R.color.ink_2);
        icon.setImageResource(on ? R.drawable.ic_check : R.drawable.ic_block);
        icon.setImageTintList(android.content.res.ColorStateList.valueOf(color));
        status.setTextColor(color);
        if (on) {
            Integer today = stats.relayedTodayBySim.get(id);
            status.setText(HomeFormat.simToday(today == null ? 0 : today));
        } else {
            SimPolicy.State state = policy.stateOf(id);
            status.setText(HomeFormat.simDisabled(state == null ? 0 : state.changedAt, zone));
        }

        Toggle toggle = card.findViewById(R.id.sim_toggle);
        toggle.setChecked(on);
        toggle.setContentDescription(getString(R.string.home_sim_forward, name));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            settings.setSimEnabled(id, checked, System.currentTimeMillis());
            Toast.makeText(this, getString(checked ? R.string.sim_enabled_toast : R.string.sim_disabled_toast, name),
                    Toast.LENGTH_SHORT).show();
            main.post(this::render);
        });
        card.findViewById(R.id.sim_open).setOnClickListener(v -> startActivity(
                new Intent(this, SimActivity.class).putExtra(SimActivity.EXTRA_SUBSCRIPTION_ID, id)));
        container.addView(card);
    }

    private void syncNow() {
        syncNow.setEnabled(false);
        Toast.makeText(this, R.string.sync_running, Toast.LENGTH_SHORT).show();
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
                render();
            });
        });
    }

}
