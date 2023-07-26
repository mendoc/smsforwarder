package com.dimitriongoua.smsforwarder.config;

public class EndPoints {
    public static final String BOT_URL = "https://api.telegram.org/bot{BOT_TOKEN}/sendMessage";
    public static final String WEBHOOK_URL = "https://miango.netlify.app/.netlify/functions/smshandler";
}
