package com.dimitriongoua.smsforwarder.destination;

import com.dimitriongoua.smsforwarder.journal.HomeFormat;
import com.dimitriongoua.smsforwarder.send.SendOutcome;

import java.util.TimeZone;

/**
 * Textes de l'écran Destinations (maquette « Destinations »). Classe sans dépendance
 * Android, testée par DestinationFormatTest.
 */
public final class DestinationFormat {
    private DestinationFormat() {
    }

    /** « Miango · reçus », « Miango · SMS des SIM », sinon l'hôte de l'URL. */
    public static String name(UrlDestination url) {
        if (DestinationStore.SMS_HANDLER_ID.equals(url.getId())) return "Miango · reçus";
        if (DestinationStore.SMS_INCOMING_ID.equals(url.getId())) return "Miango · SMS des SIM";
        String shown = shortUrl(url.getLabel());
        int slash = shown.indexOf('/');
        return slash > 0 ? shown.substring(0, slash) : shown;
    }

    /** URL affichable sans le schéma : « miango.netlify.app/smshandler ». */
    public static String shortUrl(String label) {
        if (label == null) return "";
        return label.replaceFirst("^https?://", "");
    }

    /** « X-Sms-Token : •••••••• » */
    public static String header(String name) {
        return name + " : ••••••••";
    }

    /** « Bot ••••7Hk · conversation 123 », sans jamais montrer le token. */
    public static String telegram(String token, String chatId) {
        boolean hasToken = token != null && token.length() > 3 && !token.equals("default_value");
        boolean hasChat = chatId != null && !chatId.trim().isEmpty() && !chatId.equals("default_value");
        if (!hasToken && !hasChat) return "Token du bot et conversation à renseigner";
        String bot = hasToken ? "Bot ••••" + token.substring(token.length() - 3) : "Token du bot à renseigner";
        String chat = hasChat ? "conversation " + chatId.trim() : "conversation à renseigner";
        return bot + " · " + chat;
    }

    /** Pied de carte : envois en attente, sinon dernier envoi réussi. */
    public static String status(int open, long lastSent, long now, TimeZone zone) {
        if (open > 0) return open + (open > 1 ? " envois en attente, sans réponse" : " envoi en attente, sans réponse");
        if (lastSent <= 0) return "Aucun envoi réussi pour l'instant";
        String when = HomeFormat.syncTime(lastSent, now, zone);
        return "Dernier envoi réussi " + (when.contains("/") ? "le " + when.replace(" ", " à ") : "à " + when);
    }

    /** Résultat du bouton « Tester ». */
    public static String test(boolean telegram, SendOutcome outcome) {
        int code = outcome.getHttpCode();
        if (outcome.getStatus() == SendOutcome.Status.RETRY) return "Injoignable : " + outcome.getError();
        if (telegram) {
            if (outcome.isSent()) return "Message de test envoyé sur Telegram";
            if (code == 401 || code == 404) return "Token du bot refusé (HTTP " + code + ")";
            if (code == 400 || code == 403) return "Conversation introuvable ou bot bloqué (HTTP " + code + ")";
            return "Échec : " + outcome.getError();
        }
        if (code == 401 || code == 403) return "Accès refusé : vérifiez l'en-tête (HTTP " + code + ")";
        if (code > 0) return "Destination joignable (réponse HTTP " + code + ")";
        return "Échec : " + outcome.getError();
    }
}
