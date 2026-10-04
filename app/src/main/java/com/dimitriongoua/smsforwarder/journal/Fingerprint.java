package com.dimitriongoua.smsforwarder.journal;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Empreinte d'un SMS : expéditeur, horodatage du centre SMS et corps. Elle est identique
 * pour le SMS reçu en direct et pour le même SMS relu dans la boîte de réception (colonne
 * date_sent), ce qui permet de reconnaître un SMS déjà relayé.
 */
public final class Fingerprint {
    private Fingerprint() {
    }

    public static String of(String sender, long timestamp, String body) {
        String text = (sender == null ? "" : sender.trim()) + "\n" + timestamp + "\n" + (body == null ? "" : body);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(text.getBytes(Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
