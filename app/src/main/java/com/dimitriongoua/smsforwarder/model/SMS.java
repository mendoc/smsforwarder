package com.dimitriongoua.smsforwarder.model;

import android.telephony.SmsMessage;

import java.io.Serializable;

public class SMS implements Serializable {

    public String body;
    public String address;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String timestamp;

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public static SMS fromSmsMessage(SmsMessage smsMessage) {
        SMS sms = new SMS();
        sms.setBody(smsMessage.getMessageBody());
        sms.setAddress(smsMessage.getOriginatingAddress());
        sms.setTimestamp(String.valueOf(smsMessage.getTimestampMillis()));
        return sms;
    }

    @Override
    public String toString() {
        return "SMS{" +
                "body='" + body + '\'' +
                ", address='" + address + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
