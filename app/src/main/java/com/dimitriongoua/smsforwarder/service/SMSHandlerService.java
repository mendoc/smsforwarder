package com.dimitriongoua.smsforwarder.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import com.dimitriongoua.smsforwarder.model.SMS;
import com.dimitriongoua.smsforwarder.util.Master;

import static com.dimitriongoua.smsforwarder.config.Constants.KEY_SMS;

public class SMSHandlerService extends Service {
    private static final String TAG = SMSHandlerService.class.getSimpleName();

    public SMSHandlerService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Log.d(TAG, "Réception du message par le service");
            Bundle intentExtras = intent.getExtras();
            if (intentExtras != null) {
                SMS sms = (SMS) intentExtras.get(KEY_SMS);
                Master.with(this).forwardSMS(sms);
            }
        }
        return START_STICKY;
    }
}