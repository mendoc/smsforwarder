package com.dimitriongoua.smsforwarder.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.service.SMSHandlerService;

import static com.dimitriongoua.smsforwarder.config.Constants.KEY_SMS;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = SMSReceiver.class.getSimpleName();
    public static final String SMS_BUNDLE = "pdus";

    @Override
    public void onReceive(Context context, Intent intent) {
        Bundle intentExtras = intent.getExtras();
        if (intentExtras != null) {
            Object[] sms = (Object[]) intentExtras.get(SMS_BUNDLE);
            if (sms != null) {
                StringBuilder smsBody = new StringBuilder();
                String smsAddress = "";
                String smsTimestamp = "";
                for (Object sm : sms) {
                    SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) sm);
                    smsBody.append(smsMessage.getMessageBody());
                    smsAddress = smsMessage.getOriginatingAddress();
                    smsTimestamp = String.valueOf(smsMessage.getTimestampMillis());
                    Log.d(TAG, smsMessage.getMessageBody());
                }

                SMS newSMS = new SMS();
                newSMS.setAddress(smsAddress);
                newSMS.setBody(smsBody.toString());
                newSMS.setTimestamp(smsTimestamp);

                Intent smsIntent = new Intent(context, SMSHandlerService.class);
                smsIntent.putExtra(KEY_SMS, newSMS);
                context.startService(smsIntent);
            }
        }
    }
}