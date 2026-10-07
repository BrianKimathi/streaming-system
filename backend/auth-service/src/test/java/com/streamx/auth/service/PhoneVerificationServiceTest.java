package com.streamx.auth.service;

import com.streamx.auth.domain.PhoneVerificationOtp;
import com.streamx.auth.exception.ServiceUnavailableException;
import com.streamx.auth.repository.PhoneVerificationOtpRepository;
import com.streamx.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PhoneVerificationServiceTest {

    @Autowired
    private PhoneVerificationService phoneVerificationService;

    @Autowired
    private PhoneVerificationOtpRepository otpRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private void storeOtp(String phone, String code) {
        PhoneVerificationOtp otp = new PhoneVerificationOtp();
        otp.setPhoneNumber(phone);
        otp.setHashedOtp(passwordEncoder.encode(code));
        otp.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otp.setResendCooldownUntil(LocalDateTime.now());
        otp.setAttempts(0);
        otp.setVerified(false);
        otpRepository.save(otp);
    }

    @Test
    void testSendOtpFailsHonestlyWhenSmsIsNotConfigured() {
        String phone = "+19998887777";

        ServiceUnavailableException ex = assertThrows(ServiceUnavailableException.class,
                () -> phoneVerificationService.sendOtp(phone));

        assertEquals("SMS delivery is not configured", ex.getMessage());
        assertTrue(otpRepository.findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(phone).isEmpty());
    }

    @Test
    void testVerifyValidOtpSucceeds() {
        String phone = "+15550001111";
        storeOtp(phone, "123456");

        assertTrue(phoneVerificationService.verifyOtp(phone, "123456"));
    }

    @Test
    void testVerifyInvalidOtpThrowsBadRequest() {
        String phone = "+15554443333";
        storeOtp(phone, "654321");

        assertThrows(BadRequestException.class, () -> phoneVerificationService.verifyOtp(phone, "000000"));
    }
}
