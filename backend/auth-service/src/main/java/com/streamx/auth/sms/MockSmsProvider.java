package com.streamx.auth.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(MockSmsProvider.class);

    private final Map<String, String> lastSentSms = new ConcurrentHashMap<>();

    @Override
    public void sendSms(String phoneNumber, String message) {
        log.info("[MOCK SMS PROVIDER] Sending SMS to {}: {}", phoneNumber, message);
        lastSentSms.put(phoneNumber, message);
    }

    public String getLastSentSms(String phoneNumber) {
        return lastSentSms.get(phoneNumber);
    }
}
