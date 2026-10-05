package com.dimitriongoua.smsforwarder.activity;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.Secrets;
import com.dimitriongoua.smsforwarder.destination.TelegramDestination;
import com.dimitriongoua.smsforwarder.destination.UrlDestination;

/**
 * Destinations des SMS relayés : liste d'URL (ajout, modification, suppression,
 * activation) et configuration Telegram. Les secrets ne sont jamais réaffichés : un
 * champ secret laissé vide conserve la valeur enregistrée.
 */
public class DestinationsActivity extends AppCompatActivity {
    private DestinationStore store;
    private LinearLayout urlList;
    private CheckBox telegramEnabled;
    private TextView telegramTokenStatus;
    private EditText telegramToken;
    private EditText telegramChatId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destinations);
        setTitle(R.string.destinations_title);
        TabBar.bind(this, TabBar.Tab.DESTINATIONS);
        store = DestinationStore.with(this);

        urlList = findViewById(R.id.url_list);
        telegramEnabled = findViewById(R.id.telegram_enabled);
        telegramTokenStatus = findViewById(R.id.telegram_token_status);
        telegramToken = findViewById(R.id.telegram_token);
        telegramChatId = findViewById(R.id.telegram_chat_id);
        findViewById(R.id.add_url).setOnClickListener(v -> editUrl(null));
        findViewById(R.id.save_telegram).setOnClickListener(v -> saveTelegram());

        renderUrls();
        renderTelegram();
    }

    private void renderUrls() {
        urlList.removeAllViews();
        if (store.getUrls().isEmpty()) {
            urlList.addView(text(getString(R.string.urls_none), false));
            return;
        }
        for (UrlDestination url : store.getUrls()) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(0, dp(10), 0, dp(10));
            row.addView(text(url.getLabel(), true));
            String details = url.hasHeader()
                    ? getString(R.string.url_header, url.getHeaderName(), Secrets.mask(url.getHeaderValue()))
                    : getString(R.string.url_no_header);
            if (!url.isEnabled()) details += " · " + getString(R.string.url_disabled);
            row.addView(text(details, false));
            row.setAlpha(url.isEnabled() ? 1f : 0.5f);
            row.setClickable(true);
            row.setOnClickListener(v -> editUrl(url));
            urlList.addView(row);
        }
    }

    private TextView text(String value, boolean main) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(main ? 15 : 13);
        if (!main) view.setAlpha(0.7f);
        return view;
    }

    /** Ajout (destination null) ou modification d'une URL. */
    private void editUrl(UrlDestination existing) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), 0);

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
                renderUrls();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.show();
        // Bouton remplacé après show() : une saisie invalide garde la fenêtre ouverte.
        Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        save.setOnClickListener(v -> {
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
            renderUrls();
        });
    }

    private EditText field(int hint, int inputType) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setSingleLine(true);
        return field;
    }

    private void renderTelegram() {
        TelegramDestination telegram = store.getTelegram();
        telegramEnabled.setChecked(telegram.isEnabled());
        telegramToken.setText("");
        telegramChatId.setText(telegram.getChatId());
        telegramTokenStatus.setText(telegram.getBotToken().isEmpty()
                ? getString(R.string.telegram_token_missing)
                : getString(R.string.telegram_token_saved, Secrets.mask(telegram.getBotToken())));
        telegramTokenStatus.setVisibility(View.VISIBLE);
    }

    private void saveTelegram() {
        TelegramDestination current = store.getTelegram();
        String token = telegramToken.getText().toString().trim();
        if (token.isEmpty()) token = current.getBotToken();
        TelegramDestination telegram = new TelegramDestination(token,
                telegramChatId.getText().toString(), telegramEnabled.isChecked());
        if (telegram.isEnabled() && !telegram.isActive()) {
            Toast.makeText(this, R.string.telegram_incomplete, Toast.LENGTH_LONG).show();
            return;
        }
        store.setTelegram(telegram);
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        renderTelegram();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
