package com.streamx.auth.service;

import com.streamx.auth.domain.PhoneVerificationOtp;
import com.streamx.auth.repository.AccountRepository;
import com.streamx.auth.repository.PhoneVerificationOtpRepository;
import com.streamx.auth.sms.SmsProvider;
import com.streamx.common.exception.BadRequestException;
import com.streamx.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class PhoneVerificationService {

    private static final Logger log = LoggerFactory.getLogger(PhoneVerificationService.class);

    private final PhoneVerificationOtpRepository otpRepository;
    private final AccountRepository accountRepository;
    private final SmsProvider smsProvider;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.otp.expiration-minutes:5}")
    private int otpExpirationMinutes;

    @Value("${app.otp.max-attempts:3}")
    private int maxAttempts;

    @Value("${app.otp.cooldown-seconds:60}")
    private int cooldownSeconds;

    public PhoneVerificationService(PhoneVerificationOtpRepository otpRepository,
                                    AccountRepository accountRepository,
                                    SmsProvider smsProvider,
                                    PasswordEncoder passwordEncoder) {
        this.otpRepository = otpRepository;
        this.accountRepository = accountRepository;
        this.smsProvider = smsProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void sendOtp(String phoneNumber) {
        Optional<PhoneVerificationOtp> existingOpt = otpRepository.findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(phoneNumber);

        if (existingOpt.isPresent()) {
            PhoneVerificationOtp existing = existingOpt.get();
            if (existing.getResendCooldownUntil() != null && existing.getResendCooldownUntil().isAfter(LocalDateTime.now())) {
                throw new BadRequestException("Please wait before requesting another OTP code.");
            }
        }

        String rawOtp = String.format("%06d", secureRandom.nextInt(1_000_000));
        String hashedOtp = passwordEncoder.encode(rawOtp);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusMinutes(otpExpirationMinutes);
        LocalDateTime cooldownUntil = now.plusSeconds(cooldownSeconds);

        PhoneVerificationOtp otpEntity = new PhoneVerificationOtp();
        otpEntity.setPhoneNumber(phoneNumber);
        otpEntity.setHashedOtp(hashedOtp);
        otpEntity.setExpiresAt(expiresAt);
        otpEntity.setResendCooldownUntil(cooldownUntil);
        otpEntity.setAttempts(0);
        otpEntity.setVerified(false);

        otpRepository.save(otpEntity);

        String message = String.format("Your StreamX verification code is: %s. Valid for %d minutes.", rawOtp, otpExpirationMinutes);
        smsProvider.sendSms(phoneNumber, message);
    }

    @Transactional
    public boolean verifyOtp(String phoneNumber, String code) {
        PhoneVerificationOtp otp = otpRepository.findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(phoneNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No active OTP request found for phone number"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("OTP has expired. Please request a new code.");
        }

        if (otp.getAttempts() >= maxAttempts) {
            throw new BadRequestException("Maximum OTP verification attempts exceeded. Please request a new code.");
        }

        if (!passwordEncoder.matches(code, otp.getHashedOtp())) {
            otp.setAttempts(otp.getAttempts() + 1);
            otpRepository.save(otp);
            throw new BadRequestException("Invalid OTP code.");
        }

        otp.setVerified(true);
        otpRepository.save(otp);

        accountRepository.findByPhoneNumber(phoneNumber).ifPresent(account -> {
            account.setPhoneVerified(true);
            accountRepository.save(account);
        });

        return true;
    }
}
