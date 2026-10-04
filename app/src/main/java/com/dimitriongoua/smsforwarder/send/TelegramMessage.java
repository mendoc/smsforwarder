package com.dimitriongoua.smsforwarder.send;

/**
 * Texte du message Telegram d'un SMS relayé (texte brut) :
 * <pre>
 * corps du SMS
 *
 * expéditeur
 * SIM · téléphone
 * date
 * </pre>
 * Classe sans dépendance Android, testée par TelegramMessageTest.
 */
public final class TelegramMessage {
    private TelegramMessage() {
    }

    public static String format(String body, String sender, String simLabel, String deviceName, String time) {
        StringBuilder text = new StringBuilder();
        text.append(body == null ? "" : body).append("\n\n").append(sender == null ? "" : sender);
        String origin = join(simLabel, deviceName);
        if (!origin.isEmpty()) text.append('\n').append(origin);
        if (time != null && !time.isEmpty()) text.append('\n').append(time);
        return text.toString();
    }

    /**
     * Nom affiché de la SIM : nom donné dans l'application, sinon emplacement et opérateur
     * (« SIM 1 · Airtel »), sinon « SIM inconnue ».
     */
    public static String simLabel(String name, int slot, String carrier) {
        if (!isBlank(name)) return name.trim();
        String label = slot >= 0 ? "SIM " + (slot + 1) : "SIM inconnue";
        return isBlank(carrier) ? label : label + " · " + carrier.trim();
    }

    private static String join(String first, String second) {
        if (isBlank(first)) return isBlank(second) ? "" : second.trim();
        return isBlank(second) ? first.trim() : first.trim() + " · " + second.trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
