package com.dimitriongoua.smsforwarder.util;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Identifiant du téléphone envoyé à Miango, dérivé d'ANDROID_ID.
 *
 * Depuis Android 8, ANDROID_ID est propre au couple (clé de signature de l'APK, utilisateur
 * du téléphone). Il ne change pas quand l'application est désinstallée puis réinstallée,
 * tant que l'APK est signé avec la même clé. Seule une réinitialisation du téléphone le
 * change. Il est haché pour ne jamais être transmis tel quel.
 * Classe sans dépendance Android, testée par DeviceIdentityTest.
 */
public final class DeviceIdentity {
    // Valeur renvoyée par certains anciens appareils pour tous les téléphones.
    private static final String BROKEN_ANDROID_ID = "9774d56d682e549c";
    private static final String PREFIX = "a-";

    private DeviceIdentity() {
    }

    /** @return l'identifiant dérivé, ou null si ANDROID_ID est absent ou inutilisable */
    public static String fromAndroidId(String androidId, String packageName) {
        if (androidId == null) return null;
        String value = androidId.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty() || value.equals(BROKEN_ANDROID_ID) || value.matches("0+")) return null;
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((packageName + ":" + value).getBytes(Charset.forName("UTF-8")));
            StringBuilder hex = new StringBuilder(PREFIX);
            for (int i = 0; i < 16; i++) hex.append(String.format("%02x", hash[i]));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
