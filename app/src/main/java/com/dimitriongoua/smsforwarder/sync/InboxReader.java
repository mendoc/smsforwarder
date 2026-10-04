package com.dimitriongoua.smsforwarder.sync;

import android.content.Context;
import android.database.Cursor;
import android.provider.Telephony;
import android.util.Log;

import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.util.SimResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Lecture de la boîte de réception (content://sms/inbox, permission READ_SMS), toutes SIM
 * confondues. Chaque SMS lu porte sa SIM (colonne sub_id, ou sim_id selon le constructeur).
 */
class InboxReader {
    private static final String TAG = InboxReader.class.getSimpleName();
    private static final String[] SUBSCRIPTION_COLUMNS = {"sub_id", "sim_id"};

    private InboxReader() {
    }

    /** SMS reçus depuis {@code from} (heure du téléphone), du plus ancien au plus récent. */
    static List<SMS> readSince(Context context, long from) {
        List<SMS> messages = new ArrayList<>();
        try (Cursor cursor = context.getContentResolver().query(
                Telephony.Sms.Inbox.CONTENT_URI,
                null,
                Telephony.Sms.DATE + " >= ?",
                new String[]{String.valueOf(from)},
                Telephony.Sms.DATE + " ASC")) {
            if (cursor == null) return messages;
            int address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS);
            int body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY);
            int date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE);
            int dateSent = cursor.getColumnIndex(Telephony.Sms.DATE_SENT);
            int subscription = -1;
            for (String column : SUBSCRIPTION_COLUMNS) {
                subscription = cursor.getColumnIndex(column);
                if (subscription >= 0) break;
            }
            while (cursor.moveToNext()) {
                long receivedAt = cursor.getLong(date);
                long sentAt = dateSent >= 0 ? cursor.getLong(dateSent) : 0;
                SMS sms = new SMS();
                sms.setAddress(cursor.getString(address));
                sms.setBody(cursor.getString(body) == null ? "" : cursor.getString(body));
                // Même horodatage qu'à la réception (centre SMS) : même empreinte dans le
                // journal et même dédoublonnage côté /sms/incoming.
                sms.setTimestamp(String.valueOf(sentAt > 0 ? sentAt : receivedAt));
                sms.setReceivedAt(receivedAt);
                int subscriptionId = subscription >= 0 && !cursor.isNull(subscription)
                        ? cursor.getInt(subscription) : SimResolver.UNKNOWN;
                SimResolver.fill(context, sms, subscriptionId);
                messages.add(sms);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Lecture des SMS refusée (permission READ_SMS)");
        }
        return messages;
    }
}
