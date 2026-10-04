package com.dimitriongoua.smsforwarder.destination;

/**
 * Destination Telegram : token du bot et identifiant de la conversation.
 * Classe sans dépendance Android.
 */
public final class TelegramDestination {
    public static final String KEY = "telegram";
    public static final String LABEL = "Telegram";

    private final String botToken;
    private final String chatId;
    private final boolean enabled;

    public TelegramDestination(String botToken, String chatId, boolean enabled) {
        this.botToken = botToken == null ? "" : botToken.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
        this.enabled = enabled;
    }

    /** Active et complète : token et conversation renseignés. */
    public boolean isActive() {
        return enabled && !botToken.isEmpty() && !chatId.isEmpty();
    }

    public String getBotToken() {
        return botToken;
    }

    public String getChatId() {
        return chatId;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
