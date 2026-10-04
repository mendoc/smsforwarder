package com.dimitriongoua.smsforwarder.config;

import com.dimitriongoua.smsforwarder.BuildConfig;

public class Constants {
    public static final String BOT_TOKEN = BuildConfig.BOT_TOKEN;
    public static final String CHAT_ID = BuildConfig.CHAT_ID;
    // Secret partagé avec Miango (variable SMS_INGEST_TOKEN des deux côtés).
    public static final String SMS_INGEST_TOKEN = BuildConfig.SMS_INGEST_TOKEN;
    public static final String KEY_SMS = "sms_body";

    // Expéditeurs autorisés par défaut, modifiables ensuite depuis l'écran principal.
    public static final String SMS_ADDRESS = "AirtelMoney|Paypal|NETFLIX|38643|38880";
}
