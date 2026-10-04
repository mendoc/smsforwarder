package com.dimitriongoua.smsforwarder.config;

public class EndPoints {
    public static final String BOT_URL = "https://api.telegram.org/bot{BOT_TOKEN}/sendMessage";
    public static final String WEBHOOK_URL = "https://miango.netlify.app/smshandler";
    // Outil « SMS » de la console Miango : enregistre chaque SMS relayé et sa SIM.
    public static final String INBOX_URL = "https://miango.netlify.app/sms/incoming";
}
