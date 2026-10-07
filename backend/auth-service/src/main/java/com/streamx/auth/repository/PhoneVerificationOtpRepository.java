package com.streamx.auth.repository;

import com.streamx.auth.domain.PhoneVerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PhoneVerificationOtpRepository extends JpaRepository<PhoneVerificationOtp, UUID> {
    Optional<PhoneVerificationOtp> findFirstByPhoneNumberAndVerifiedFalseOrderByCreatedAtDesc(String phoneNumber);
    void deleteByPhoneNumber(String phoneNumber);
}
