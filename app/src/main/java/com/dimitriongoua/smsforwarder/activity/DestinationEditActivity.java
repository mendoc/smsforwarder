package com.dimitriongoua.smsforwarder.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.dimitriongoua.smsforwarder.R;
import com.dimitriongoua.smsforwarder.destination.DestinationFormat;
import com.dimitriongoua.smsforwarder.destination.DestinationStore;
import com.dimitriongoua.smsforwarder.destination.UrlDestination;
import com.dimitriongoua.smsforwarder.widget.Toggle;

/**
 * Modifier une destination (maquette « Modifier une destination ») : titre affiché dans le
 * journal, URL, en-tête de sécurité et état. Sans identifiant, crée une destination. La
 * valeur de l'en-tête n'est jamais réaffichée : laissée vide, elle garde la valeur enregistrée.
 */
public class DestinationEditActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "destination_id";

    private DestinationStore store;
    private UrlDestination existing;
    private EditText title;
    private EditText url;
    private EditText headerName;
    private EditText headerValue;
    private Toggle enabled;

    public static Intent intent(Context context, String id) {
        Intent intent = new Intent(context, DestinationEditActivity.class);
        if (id != null) intent.putExtra(EXTRA_ID, id);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_destination_edit);
        store = DestinationStore.with(this);
        String id = getIntent().getStringExtra(EXTRA_ID);
        existing = id == null ? null : store.findUrl(id);

        title = findViewById(R.id.edit_title_field);
        url = findViewById(R.id.edit_url_field);
        headerName = findViewById(R.id.edit_header_name);
        headerValue = findViewById(R.id.edit_header_value);
        enabled = findViewById(R.id.edit_enabled);
        findViewById(R.id.edit_back).setOnClickListener(v -> finish());
        findViewById(R.id.edit_enabled_row).setOnClickListener(v -> enabled.toggle());
        findViewById(R.id.edit_save).setOnClickListener(v -> save());

        boolean hasValue = existing != null && existing.getHeaderValue() != null && !existing.getHeaderValue().isEmpty();
        findViewById(R.id.edit_header_keep).setVisibility(hasValue ? View.VISIBLE : View.GONE);
        if (hasValue) headerValue.setHint(R.string.url_header_value_saved);
        View delete = findViewById(R.id.edit_delete);
        delete.setVisibility(existing == null ? View.GONE : View.VISIBLE);
        delete.setOnClickListener(v -> confirmDelete());
        ((TextView) findViewById(R.id.edit_heading)).setText(existing == null ? R.string.url_new_heading : R.string.url_edit_heading);

        if (savedInstanceState != null) return;
        enabled.setChecked(existing == null || existing.isEnabled());
        if (existing != null) {
            title.setText(shownTitle(existing));
            url.setText(existing.getUrl());
            headerName.setText(existing.getHeaderName() == null ? "" : existing.getHeaderName());
        }
    }

    /** Titre choisi ou titre par défaut des destinations Miango ; vide (l'hôte s'affiche) sinon. */
    private static String shownTitle(UrlDestination destination) {
        if (destination.getTitle() != null) return destination.getTitle();
        if (DestinationStore.SMS_HANDLER_ID.equals(destination.getId())
                || DestinationStore.SMS_INCOMING_ID.equals(destination.getId())) {
            return DestinationFormat.name(destination);
        }
        return "";
    }

    private void save() {
        String value = headerValue.getText().toString();
        if (value.isEmpty() && existing != null && existing.getHeaderValue() != null) value = existing.getHeaderValue();
        String urlError = UrlDestination.validateUrl(url.getText().toString());
        if (urlError != null) {
            url.setError(urlError);
            url.requestFocus();
            return;
        }
        String headerError = UrlDestination.validateHeaderName(headerName.getText().toString());
        if (headerError != null) {
            headerName.setError(headerError);
            headerName.requestFocus();
            return;
        }
        store.saveUrl(new UrlDestination(
                existing == null ? null : existing.getId(),
                title.getText().toString(),
                url.getText().toString(),
                headerName.getText().toString(),
                value,
                enabled.isChecked()));
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.url_delete_confirm, DestinationFormat.name(existing)))
                .setPositiveButton(R.string.url_delete, (dialog, which) -> {
                    store.deleteUrl(existing.getId());
                    Toast.makeText(this, R.string.url_deleted, Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
