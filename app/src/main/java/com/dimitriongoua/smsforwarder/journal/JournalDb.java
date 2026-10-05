package com.dimitriongoua.smsforwarder.journal;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.send.SendOutcome;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Journal local des SMS relayés (SQLite) : une ligne par SMS, et une ligne par destination
 * avec le statut de l'envoi. Les entrées de plus de 30 jours sont purgées.
 */
public class JournalDb extends SQLiteOpenHelper {
    private static final String NAME = "journal.db";
    private static final int VERSION = 1;
    public static final long RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private static JournalDb instance;

    private JournalDb(Context context) {
        super(context.getApplicationContext(), NAME, null, VERSION);
    }

    /** Instance unique : SQLite sérialise ainsi les écritures des différents fils. */
    public static synchronized JournalDb get(Context context) {
        if (instance == null) instance = new JournalDb(context);
        return instance;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sms ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "fingerprint TEXT NOT NULL UNIQUE,"
                + "sender TEXT,"
                + "body TEXT,"
                + "timestamp INTEGER NOT NULL,"
                + "received_at INTEGER NOT NULL,"
                + "subscription_id INTEGER NOT NULL DEFAULT -1,"
                + "sim_slot INTEGER NOT NULL DEFAULT -1,"
                + "sim_carrier TEXT,"
                + "sim_number TEXT,"
                + "sim_label TEXT,"
                + "source TEXT NOT NULL)");
        db.execSQL("CREATE INDEX sms_received ON sms (received_at, id)");
        db.execSQL("CREATE TABLE delivery ("
                + "sms_id INTEGER NOT NULL REFERENCES sms (id) ON DELETE CASCADE,"
                + "dest_key TEXT NOT NULL,"
                + "dest_label TEXT NOT NULL,"
                + "status TEXT NOT NULL,"
                + "attempts INTEGER NOT NULL DEFAULT 0,"
                + "last_attempt_at INTEGER NOT NULL DEFAULT 0,"
                + "last_error TEXT,"
                + "via TEXT,"
                + "PRIMARY KEY (sms_id, dest_key))");
        db.execSQL("CREATE INDEX delivery_status ON delivery (status)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Première version du schéma.
    }

    /**
     * Enregistre un SMS (ou retrouve celui qui a la même empreinte) et ajoute une ligne
     * « en attente » pour chaque destination qui n'en a pas encore.
     *
     * @param destinations clé → libellé des destinations actives
     * @return l'identifiant du SMS dans le journal
     */
    public long record(SMS sms, String simLabel, String source, Map<String, String> destinations) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String fingerprint = Fingerprint.of(sms.getAddress(), sms.getTimestampMillis(), sms.getBody());
            long id = findId(db, fingerprint);
            if (id < 0) {
                ContentValues values = new ContentValues();
                values.put("fingerprint", fingerprint);
                values.put("sender", sms.getAddress());
                values.put("body", sms.getBody());
                values.put("timestamp", sms.getTimestampMillis());
                values.put("received_at", sms.getReceivedAt());
                values.put("subscription_id", sms.getSubscriptionId());
                values.put("sim_slot", sms.getSimSlot());
                values.put("sim_carrier", sms.getSimCarrier());
                values.put("sim_number", sms.getSimNumber());
                values.put("sim_label", simLabel);
                values.put("source", source);
                id = db.insertOrThrow("sms", null, values);
            }
            for (Map.Entry<String, String> destination : destinations.entrySet()) {
                ContentValues values = new ContentValues();
                values.put("sms_id", id);
                values.put("dest_key", destination.getKey());
                values.put("dest_label", destination.getValue());
                values.put("status", DeliveryStatus.PENDING.code);
                db.insertWithOnConflict("delivery", null, values, SQLiteDatabase.CONFLICT_IGNORE);
            }
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    private static long findId(SQLiteDatabase db, String fingerprint) {
        try (Cursor cursor = db.rawQuery("SELECT id FROM sms WHERE fingerprint = ?", new String[]{fingerprint})) {
            return cursor.moveToFirst() ? cursor.getLong(0) : -1;
        }
    }

    /** Identifiant du SMS de cette empreinte dans le journal, -1 s'il n'y est pas. */
    public long findId(String fingerprint) {
        return findId(getReadableDatabase(), fingerprint);
    }

    /**
     * Le journal contient-il déjà ce SMS (même expéditeur et même corps, reçu à moins de
     * {@code windowMs} près) ? Filet de sécurité si l'horodatage diffère entre la réception
     * et la boîte de réception.
     */
    public boolean containsSimilar(String sender, String body, long receivedAt, long windowMs) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM sms WHERE sender = ? AND body = ? AND received_at BETWEEN ? AND ? LIMIT 1",
                new String[]{sender == null ? "" : sender, body == null ? "" : body,
                        String.valueOf(receivedAt - windowMs), String.valueOf(receivedAt + windowMs)})) {
            return cursor.moveToFirst();
        }
    }

    /** Statut d'envoi d'un SMS vers une destination, null si aucune ligne. */
    public DeliveryStatus status(long smsId, String destinationKey) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT status FROM delivery WHERE sms_id = ? AND dest_key = ?",
                new String[]{String.valueOf(smsId), destinationKey})) {
            return cursor.moveToFirst() ? DeliveryStatus.fromCode(cursor.getString(0)) : null;
        }
    }

    /** Nombre de destinations du SMS dans ce statut. */
    public int count(long smsId, DeliveryStatus status) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM delivery WHERE sms_id = ? AND status = ?",
                new String[]{String.valueOf(smsId), status.code})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Envois encore à faire (en attente ou à retenter), tous SMS confondus. */
    public int countOpen() {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM delivery WHERE status IN (?, ?)",
                new String[]{DeliveryStatus.PENDING.code, DeliveryStatus.RETRY.code})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Enregistre le résultat d'un essai d'envoi. */
    public void recordAttempt(long smsId, String destinationKey, SendOutcome outcome, long now, String via) {
        DeliveryStatus status = DeliveryStatus.from(outcome);
        SQLiteDatabase db = getWritableDatabase();
        db.execSQL("UPDATE delivery SET attempts = attempts + 1, status = ?, last_attempt_at = ?, last_error = ?, "
                        + "via = CASE WHEN ? = 'sent' THEN ? ELSE via END "
                        + "WHERE sms_id = ? AND dest_key = ?",
                new Object[]{status.code, now, outcome.getError(), status.code, via, smsId, destinationKey});
    }

    /** Change le statut sans compter d'essai (destination supprimée, abandon…). */
    public void close(long smsId, String destinationKey, String reason) {
        getWritableDatabase().execSQL(
                "UPDATE delivery SET status = ?, last_error = ? WHERE sms_id = ? AND dest_key = ? AND status IN (?, ?)",
                new Object[]{DeliveryStatus.FAILED.code, reason, smsId, destinationKey,
                        DeliveryStatus.PENDING.code, DeliveryStatus.RETRY.code});
    }

    /** Supprime les entrées reçues il y a plus de 30 jours. */
    public int purge(long now) {
        return getWritableDatabase().delete("sms", "received_at < ?", new String[]{String.valueOf(now - RETENTION_MS)});
    }

    /**
     * Page du journal, de la plus récente à la plus ancienne.
     *
     * @param before dernière entrée de la page précédente, null pour la première page
     */
    public List<JournalEntry> page(JournalEntry before, int limit) {
        return page(before, limit, JournalQuery.ALL);
    }

    /** Page du journal restreinte aux SMS retenus par {@code query}. */
    public List<JournalEntry> page(JournalEntry before, int limit, JournalQuery query) {
        List<String> conditions = new ArrayList<>();
        List<String> args = new ArrayList<>();
        query.appendConditions(conditions, args);
        if (before != null) {
            conditions.add("(received_at < ? OR (received_at = ? AND id < ?))");
            args.add(String.valueOf(before.receivedAt));
            args.add(String.valueOf(before.receivedAt));
            args.add(String.valueOf(before.id));
        }
        String where = conditions.isEmpty() ? "" : "WHERE " + TextUtils.join(" AND ", conditions) + " ";
        return entries("SELECT * FROM sms " + where + "ORDER BY received_at DESC, id DESC LIMIT " + limit,
                args.isEmpty() ? null : args.toArray(new String[0]));
    }

    /** SIM présentes dans le journal (identifiant d'abonnement → nom), la plus récente d'abord. */
    public Map<Integer, String> sims() {
        Map<Integer, String> sims = new LinkedHashMap<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT subscription_id, sim_slot, sim_carrier, sim_label, MAX(received_at) AS last FROM sms "
                        + "GROUP BY subscription_id ORDER BY last DESC", null)) {
            while (cursor.moveToNext()) sims.put(cursor.getInt(0), simLabel(cursor));
        }
        return sims;
    }

    /** Tests uniquement : ferme l'instance unique pour repartir d'une base neuve. */
    public static synchronized void resetForTests() {
        if (instance != null) instance.close();
        instance = null;
    }

    /** Entrées dont au moins un envoi est encore à faire, de la plus ancienne à la plus récente. */
    public List<JournalEntry> open() {
        return entries("SELECT * FROM sms WHERE id IN (SELECT sms_id FROM delivery WHERE status IN ('"
                + DeliveryStatus.PENDING.code + "', '" + DeliveryStatus.RETRY.code + "')) "
                + "ORDER BY received_at, id", null);
    }

    private List<JournalEntry> entries(String sql, String[] args) {
        SQLiteDatabase db = getReadableDatabase();
        Map<Long, JournalEntry> byId = new LinkedHashMap<>();
        try (Cursor cursor = db.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                JournalEntry entry = new JournalEntry(
                        cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("sender")),
                        cursor.getString(cursor.getColumnIndexOrThrow("body")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("subscription_id")),
                        simLabel(cursor),
                        cursor.getString(cursor.getColumnIndexOrThrow("source")));
                entry.simSlot = cursor.getInt(cursor.getColumnIndexOrThrow("sim_slot"));
                entry.simCarrier = cursor.getString(cursor.getColumnIndexOrThrow("sim_carrier"));
                entry.simNumber = cursor.getString(cursor.getColumnIndexOrThrow("sim_number"));
                byId.put(entry.id, entry);
            }
        }
        if (byId.isEmpty()) return new ArrayList<>();
        StringBuilder ids = new StringBuilder();
        for (Long id : byId.keySet()) ids.append(ids.length() == 0 ? "" : ",").append(id);
        try (Cursor cursor = db.rawQuery("SELECT * FROM delivery WHERE sms_id IN (" + ids + ") ORDER BY rowid", null)) {
            while (cursor.moveToNext()) {
                JournalEntry entry = byId.get(cursor.getLong(cursor.getColumnIndexOrThrow("sms_id")));
                if (entry == null) continue;
                entry.deliveries.add(new Delivery(
                        cursor.getString(cursor.getColumnIndexOrThrow("dest_key")),
                        cursor.getString(cursor.getColumnIndexOrThrow("dest_label")),
                        DeliveryStatus.fromCode(cursor.getString(cursor.getColumnIndexOrThrow("status"))),
                        cursor.getInt(cursor.getColumnIndexOrThrow("attempts")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("last_attempt_at")),
                        cursor.getString(cursor.getColumnIndexOrThrow("last_error")),
                        cursor.getString(cursor.getColumnIndexOrThrow("via"))));
            }
        }
        return new ArrayList<>(byId.values());
    }

    private static String simLabel(Cursor cursor) {
        String label = cursor.getString(cursor.getColumnIndexOrThrow("sim_label"));
        if (label != null && !label.isEmpty()) return label;
        int slot = cursor.getInt(cursor.getColumnIndexOrThrow("sim_slot"));
        String carrier = cursor.getString(cursor.getColumnIndexOrThrow("sim_carrier"));
        String name = slot >= 0 ? "SIM " + (slot + 1) : "SIM inconnue";
        return carrier == null || carrier.isEmpty() ? name : name + " · " + carrier;
    }
}
