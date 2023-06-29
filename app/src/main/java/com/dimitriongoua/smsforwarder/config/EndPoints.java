package com.dimitriongoua.smsforwarder.config;

public class EndPoints {
    private static final String BASE_URL = "https://api.telegram.org/bot{BOT_TOKEN}/sendMessage?chat_id={CHAT_ID}&parse_mode=Markdown&text={MESSAGE}";
    public static final String FWD_URL = BASE_URL.replace("{BOT_TOKEN}", Constants.BOT_TOKEN)
            .replace("{CHAT_ID}", Constants.CHAT_ID);
}
