package com.streamx.auth.service;

import com.streamx.auth.sms.MockSmsProvider;

import com.streamx.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PhoneVerificationServiceTest {

    @Autowired
    private PhoneVerificationService phoneVerificationService;

    @Autowired
    private MockSmsProvider mockSmsProvider;

    @Test
    void testSendOtpAndVerifySuccess() {
        String phone = "+19998887777";
        phoneVerificationService.sendOtp(phone);

        String smsBody = mockSmsProvider.getLastSentSms(phone);
        assertNotNull(smsBody);
        assertTrue(smsBody.contains("Your StreamX verification code is:"));

        // Extract 6-digit code from mock message
        String otpCode = smsBody.replaceAll(".*: (\\d{6}).*", "$1");
        assertEquals(6, otpCode.length());

        boolean verified = phoneVerificationService.verifyOtp(phone, otpCode);
        assertTrue(verified);
    }

    @Test
    void testVerifyInvalidOtpThrowsBadRequest() {
        String phone = "+15554443333";
        phoneVerificationService.sendOtp(phone);

        assertThrows(BadRequestException.class, () -> phoneVerificationService.verifyOtp(phone, "000000"));
    }
}
