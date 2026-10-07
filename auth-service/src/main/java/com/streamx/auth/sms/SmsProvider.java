package com.streamx.auth.sms;

public interface SmsProvider {
    void sendSms(String phoneNumber, String message);
}
