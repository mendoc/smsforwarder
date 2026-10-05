package com.dimitriongoua.smsforwarder.activity;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.destination.DestinationFormat;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.TelegramDestination;
import com.dimitriongoua.smsforwarder.destination.UrlDestination;
import com.dimitriongoua.smsforwarder.journal.JournalDb;
import com.dimitriongoua.smsforwarder.send.Forwarder;
import com.dimitriongoua.smsforwarder.send.SendOutcome;
import com.dimitriongoua.smsforwarder.widget.Toggle;

import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Destinations (maquette « Destinations ») : une carte par destination (les URL, puis
 * Telegram) avec interrupteur, en-tête masqué, dernier envoi réussi ou envois en attente, et
 * bouton « Tester ». Toucher une carte la modifie ; les secrets ne sont jamais réaffichés
 * (un champ secret laissé vide conserve la valeur enregistrée).
 */
public class DestinationsActivity extends AppCompatActivity {
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private DestinationStore store;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destinations);
        TabBar.bind(this, TabBar.Tab.DESTINATIONS);
        store = DestinationStore.with(this);
        list = findViewById(R.id.dest_list);
        findViewById(R.id.dest_add).setOnClickListener(v -> editUrl(null));
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        READER.execute(() -> {
            Map<String, long[]> stats = JournalDb.get(this).destinationStats();
            main.post(() -> {
                if (isFinishing()) return;
                bind(stats);
            });
        });
    }

    private void bind(Map<String, long[]> stats) {
        list.removeAllViews();
        long now = System.currentTimeMillis();
        TimeZone zone = TimeZone.getDefault();
        for (UrlDestination url : store.getUrls()) {
            addCard(url.getKey(), DestinationFormat.name(url), DestinationFormat.shortUrl(url.getLabel()), true,
                    url.hasHeader() ? DestinationFormat.header(url.getHeaderName()) : null,
                    url.isEnabled(), stats.get(url.getKey()), now, zone, v -> editUrl(url),
                    checked -> {
                        store.saveUrl(url.withEnabled(checked));
                        render();
                    });
        }
        TelegramDestination telegram = store.getTelegram();
        addCard(TelegramDestination.KEY, TelegramDestination.LABEL,
                DestinationFormat.telegram(telegram.getBotToken(), telegram.getChatId()), false, null,
                telegram.isEnabled(), stats.get(TelegramDestination.KEY), now, zone, v -> editTelegram(),
                checked -> setTelegramEnabled(checked));
    }

    private interface OnSwitch {
        void set(boolean checked);
    }

    private void addCard(String key, String name, String detail, boolean isUrl, String header, boolean enabled,
                         long[] stats, long now, TimeZone zone, View.OnClickListener open, OnSwitch onSwitch) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_destination, list, false);
        long lastSent = stats == null ? 0 : stats[0];
        int pending = stats == null ? 0 : (int) stats[1];
        card.setBackgroundResource(pending > 0 ? R.drawable.bg_card_open : R.drawable.bg_card);
        card.findViewById(R.id.dest_icon_box).setBackgroundResource(isUrl ? R.drawable.bg_icon_url : R.drawable.bg_icon_telegram);
        card.findViewById(R.id.dest_icon_text).setVisibility(isUrl ? View.VISIBLE : View.GONE);
        card.findViewById(R.id.dest_icon_image).setVisibility(isUrl ? View.GONE : View.VISIBLE);
        ((TextView) card.findViewById(R.id.dest_name)).setText(name);
        TextView detailView = card.findViewById(R.id.dest_url);
        detailView.setText(detail);
        if (!isUrl) {
            detailView.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.plex_sans_regular));
            detailView.setTextSize(13);
        }
        TextView headerView = card.findViewById(R.id.dest_header);
        headerView.setVisibility(header == null ? View.GONE : View.VISIBLE);
        if (header != null) headerView.setText(header);

        ImageView icon = card.findViewById(R.id.dest_status_icon);
        TextView status = card.findViewById(R.id.dest_status);
        int color = ContextCompat.getColor(this, pending > 0 ? R.color.amber_ink : lastSent > 0 ? R.color.pine : R.color.ink_2);
        icon.setImageResource(pending > 0 ? R.drawable.ic_clock : R.drawable.ic_check);
        icon.setVisibility(pending > 0 || lastSent > 0 ? View.VISIBLE : View.GONE);
        icon.setImageTintList(ColorStateList.valueOf(color));
        status.setText(DestinationFormat.status(pending, lastSent, now, zone));
        status.setTextColor(color);

        Toggle toggle = card.findViewById(R.id.dest_toggle);
        toggle.setChecked(enabled);
        toggle.setContentDescription(getString(R.string.destination_toggle, name));
        toggle.setOnCheckedChangeListener((button, checked) -> onSwitch.set(checked));
        card.findViewById(R.id.dest_open).setOnClickListener(open);
        Button test = card.findViewById(R.id.dest_test);
        test.setOnClickListener(v -> test(key, !isUrl, test));
        list.addView(card);
    }

    /** Envoi de test hors journal (Forwarder.testTarget), résultat en toast. */
    private void test(String key, boolean telegram, Button button) {
        Forwarder forwarder = Forwarder.with(this);
        Forwarder.Target target = forwarder.testTarget(key);
        if (target == null) return;
        button.setEnabled(false);
        Toast.makeText(this, R.string.destination_testing, Toast.LENGTH_SHORT).show();
        Forwarder.EXECUTOR.execute(() -> {
            SendOutcome outcome = forwarder.send(target);
            main.post(() -> {
                if (isFinishing()) return;
                button.setEnabled(true);
                Toast.makeText(this, DestinationFormat.test(telegram, outcome), Toast.LENGTH_LONG).show();
            });
        });
    }

    private void setTelegramEnabled(boolean checked) {
        TelegramDestination current = store.getTelegram();
        TelegramDestination telegram = new TelegramDestination(current.getBotToken(), current.getChatId(), checked);
        if (checked && !telegram.isActive()) {
            Toast.makeText(this, R.string.telegram_incomplete, Toast.LENGTH_LONG).show();
            editTelegram();
            render();
            return;
        }
        store.setTelegram(telegram);
        render();
    }

    /** Token (jamais réaffiché) et conversation Telegram. */
    private void editTelegram() {
        TelegramDestination current = store.getTelegram();
        LinearLayout form = form();
        TextView status = new TextView(this);
        status.setText(current.getBotToken().isEmpty() ? getString(R.string.telegram_token_missing)
                : getString(R.string.telegram_token_saved, com.dimitriongoua.smsforwarder.destination.Secrets.mask(current.getBotToken())));
        EditText token = field(R.string.telegram_token_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        EditText chat = field(R.string.telegram_chat_hint, InputType.TYPE_CLASS_TEXT);
        chat.setText(current.getChatId());
        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.destination_enabled);
        enabled.setChecked(current.isEnabled());
        form.addView(status);
        form.addView(token);
        form.addView(chat);
        form.addView(enabled);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.telegram_edit_title)
                .setView(form)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = token.getText().toString().trim();
            if (value.isEmpty()) value = current.getBotToken();
            TelegramDestination telegram = new TelegramDestination(value, chat.getText().toString(), enabled.isChecked());
            if (telegram.isEnabled() && !telegram.isActive()) {
                Toast.makeText(this, R.string.telegram_incomplete, Toast.LENGTH_LONG).show();
                return;
            }
            store.setTelegram(telegram);
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            render();
        });
    }

    /** Ajout (destination null) ou modification d'une URL. */
    private void editUrl(UrlDestination existing) {
        LinearLayout form = form();
        EditText url = field(R.string.url_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText headerName = field(R.string.url_header_name_hint, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        boolean hasValue = existing != null && existing.getHeaderValue() != null && !existing.getHeaderValue().isEmpty();
        EditText headerValue = field(hasValue ? R.string.url_header_value_keep : R.string.url_header_value_hint,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        CheckBox enabled = new CheckBox(this);
        enabled.setText(R.string.destination_enabled);
        enabled.setChecked(existing == null || existing.isEnabled());
        if (existing != null) {
            url.setText(existing.getUrl());
            headerName.setText(existing.getHeaderName() == null ? "" : existing.getHeaderName());
        }
        form.addView(url);
        form.addView(headerName);
        form.addView(headerValue);
        form.addView(enabled);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.url_edit_title)
                .setView(form)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);
        if (existing != null) {
            builder.setNeutralButton(R.string.url_delete, (dialog, which) -> {
                store.deleteUrl(existing.getId());
                Toast.makeText(this, R.string.url_deleted, Toast.LENGTH_SHORT).show();
                render();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        // Bouton remplacé après show() : une saisie invalide garde la fenêtre ouverte.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String value = headerValue.getText().toString();
            if (value.isEmpty() && existing != null && existing.getHeaderValue() != null) {
                value = existing.getHeaderValue();
            }
            UrlDestination destination = new UrlDestination(
                    existing == null ? null : existing.getId(),
                    url.getText().toString(),
                    headerName.getText().toString(),
                    value,
                    enabled.isChecked());
            String urlError = UrlDestination.validateUrl(destination.getUrl());
            String headerError = UrlDestination.validateHeaderName(headerName.getText().toString());
            if (urlError != null) {
                url.setError(urlError);
                return;
            }
            if (headerError != null) {
                headerName.setError(headerError);
                return;
            }
            store.saveUrl(destination);
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            render();
        });
    }

    private LinearLayout form() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        return form;
    }

    private EditText field(int hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setSingleLine(true);
        return field;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
