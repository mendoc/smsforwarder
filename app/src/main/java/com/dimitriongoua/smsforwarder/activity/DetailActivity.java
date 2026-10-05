package com.dimitriongoua.smsforwarder.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.journal.Delivery;
import com.dimitriongoua.smsforwarder.journal.DeliveryStatus;
import com.dimitriongoua.smsforwarder.journal.DetailFormat;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.journal.JournalEntry;
import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.Dispatcher;
import com.dimitriongoua.smsforwarder.send.Forwarder;
import com.dimitriongoua.smsforwarder.util.Settings;

import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Détail d'un SMS du journal (maquette « Détail d'un SMS ») : texte complet, SIM, opérateur,
 * mode de relais, règle qui l'a retenu, puis l'état de chaque envoi. « Réessayer » relance
 * les envois non réussis (un envoi réussi n'est jamais refait), « Copier » copie le texte.
 */
public class DetailActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "sms_id";
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private long id;
    private JournalEntry entry;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        id = getIntent().getLongExtra(EXTRA_ID, -1);
        findViewById(R.id.detail_back).setOnClickListener(v -> finish());
        findViewById(R.id.detail_retry).setOnClickListener(v -> retry());
        findViewById(R.id.detail_copy).setOnClickListener(v -> copy());
        fact(R.id.detail_fact_sim, R.string.detail_sim);
        fact(R.id.detail_fact_carrier, R.string.detail_carrier);
        fact(R.id.detail_fact_via, R.string.detail_via);
        fact(R.id.detail_fact_rule, R.string.detail_rule);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void fact(int id, int label) {
        ((TextView) findViewById(id).findViewById(R.id.fact_label)).setText(label);
    }

    private void factValue(int id, String value, boolean mono) {
        TextView view = findViewById(id).findViewById(R.id.fact_value);
        view.setText(value);
        if (mono) {
            view.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.plex_mono_regular));
            view.setTextSize(14);
        }
    }

    private void load() {
        READER.execute(() -> {
            JournalEntry found = JournalDb.get(this).entry(id);
            main.post(() -> {
                if (isFinishing()) return;
                if (found == null) {
                    Toast.makeText(this, R.string.detail_gone, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                entry = found;
                bind();
            });
        });
    }

    private void bind() {
        TimeZone zone = TimeZone.getDefault();
        ((TextView) findViewById(R.id.detail_sender)).setText(entry.sender);
        ((TextView) findViewById(R.id.detail_received)).setText(DetailFormat.received(entry.receivedAt, zone));
        ((TextView) findViewById(R.id.detail_body)).setText(entry.body);
        String sim = entry.simLabel;
        if (entry.simSlot >= 0 && !sim.startsWith("SIM ")) sim += " · SIM " + (entry.simSlot + 1);
        factValue(R.id.detail_fact_sim, sim, false);
        factValue(R.id.detail_fact_carrier, entry.simCarrier == null ? "—" : entry.simCarrier, false);
        factValue(R.id.detail_fact_via, DetailFormat.via(entry.source), false);
        String reason = Settings.with(this).getFilter().reason(entry.sender, entry.body);
        factValue(R.id.detail_fact_rule, reason == null ? "—" : reason, true);

        ((TextView) findViewById(R.id.detail_summary)).setText(DetailFormat.summary(entry.deliveries));
        LinearLayout list = findViewById(R.id.detail_deliveries);
        // Les fonds des lignes (envoi en attente) suivent les coins arrondis de la carte.
        list.setClipToOutline(true);
        list.removeAllViews();
        for (int i = 0; i < entry.deliveries.size(); i++) {
            list.addView(row(entry.deliveries.get(i), list, i == entry.deliveries.size() - 1, zone));
        }
        list.setVisibility(entry.deliveries.isEmpty() ? View.GONE : View.VISIBLE);

        String retry = DetailFormat.retry(entry.deliveries);
        TextView retryButton = findViewById(R.id.detail_retry);
        retryButton.setVisibility(retry == null ? View.GONE : View.VISIBLE);
        if (retry != null) retryButton.setText(retry);
    }

    private View row(Delivery delivery, LinearLayout parent, boolean last, TimeZone zone) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_detail_delivery, parent, false);
        boolean open = delivery.status.isOpen();
        boolean failed = delivery.status == DeliveryStatus.FAILED;
        int ink = ContextCompat.getColor(this, open ? R.color.amber_ink : failed ? R.color.red_ink : R.color.pine);
        row.findViewById(R.id.delivery_dot).setBackgroundResource(open ? R.drawable.bg_status_amber
                : failed ? R.drawable.bg_status_red : R.drawable.bg_status_pine);
        ImageView icon = row.findViewById(R.id.delivery_icon);
        icon.setImageResource(open ? R.drawable.ic_clock : failed ? R.drawable.ic_badge_close : R.drawable.ic_check);
        icon.setImageTintList(ColorStateList.valueOf(ink));
        ((TextView) row.findViewById(R.id.delivery_label)).setText(delivery.destinationLabel);
        TextView detail = row.findViewById(R.id.delivery_detail);
        detail.setText(DetailFormat.delivery(delivery));
        if (open) detail.setTextColor(ink);
        TextView time = row.findViewById(R.id.delivery_time);
        time.setText(open ? "" : DetailFormat.time(delivery.lastAttemptAt, zone));
        if (open) {
            row.findViewById(R.id.delivery_timeline).setVisibility(View.VISIBLE);
            ((TextView) row.findViewById(R.id.delivery_last)).setText(DetailFormat.lastAttempt(delivery));
            ((TextView) row.findViewById(R.id.delivery_last_time)).setText(DetailFormat.time(delivery.lastAttemptAt, zone));
            row.setBackgroundColor(ContextCompat.getColor(this, R.color.open_row));
        }
        if (!last) {
            LinearLayout wrapper = new LinearLayout(this);
            wrapper.setOrientation(LinearLayout.VERTICAL);
            wrapper.addView(row);
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.line_soft));
            wrapper.addView(divider, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                    Math.max(1, Math.round(getResources().getDisplayMetrics().density))));
            return wrapper;
        }
        return row;
    }

    /** Relance les envois non réussis sur le fil d'envoi, puis recharge l'écran. */
    private void retry() {
        if (entry == null) return;
        findViewById(R.id.detail_retry).setEnabled(false);
        Toast.makeText(this, R.string.detail_retrying, Toast.LENGTH_SHORT).show();
        final JournalEntry current = entry;
        Forwarder.EXECUTOR.execute(() -> {
            Dispatcher dispatcher = Dispatcher.with(this);
            SMS sms = toSms(current);
            for (Delivery delivery : current.deliveries) {
                if (delivery.status == DeliveryStatus.SENT) continue;
                Forwarder.Target target = dispatcher.getForwarder().targetFor(delivery.destinationKey, sms);
                if (target != null) dispatcher.deliver(current.id, target, Delivery.VIA_SYNC);
            }
            main.post(() -> {
                if (isFinishing()) return;
                findViewById(R.id.detail_retry).setEnabled(true);
                load();
            });
        });
    }

    private void copy() {
        if (entry == null) return;
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText(entry.sender, entry.body));
        Toast.makeText(this, R.string.detail_copied, Toast.LENGTH_SHORT).show();
    }

    private static SMS toSms(JournalEntry entry) {
        SMS sms = new SMS();
        sms.setAddress(entry.sender);
        sms.setBody(entry.body);
        sms.setTimestamp(String.valueOf(entry.timestamp));
        sms.setReceivedAt(entry.receivedAt);
        sms.setSubscriptionId(entry.subscriptionId);
        sms.setSimSlot(entry.simSlot);
        sms.setSimCarrier(entry.simCarrier);
        sms.setSimNumber(entry.simNumber);
        return sms;
    }
}
