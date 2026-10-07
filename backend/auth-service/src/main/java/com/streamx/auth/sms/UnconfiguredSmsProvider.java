package com.streamx.auth.sms;

import com.streamx.auth.exception.ServiceUnavailableException;
import org.springframework.stereotype.Component;

/**
 * No SMS gateway is integrated yet, so phone OTP delivery fails honestly instead of pretending to send.
 */
@Component
public class UnconfiguredSmsProvider implements SmsProvider {

    public static final String NOT_CONFIGURED_MESSAGE = "SMS delivery is not configured";

    @Override
    public void sendSms(String phoneNumber, String message) {
        throw new ServiceUnavailableException(NOT_CONFIGURED_MESSAGE);
    }
}
