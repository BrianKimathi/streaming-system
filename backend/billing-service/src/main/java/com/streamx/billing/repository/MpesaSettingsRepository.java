package com.streamx.billing.repository;

import com.streamx.billing.domain.MpesaSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaSettingsRepository extends JpaRepository<MpesaSettings, Long> {
}
