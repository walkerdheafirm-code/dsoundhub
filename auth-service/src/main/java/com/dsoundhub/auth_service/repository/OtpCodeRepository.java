package com.dsoundhub.auth_service.repository;

import com.dsoundhub.auth_service.entity.OtpCode;
import com.dsoundhub.auth_service.entity.OtpType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OtpCodeRepository extends JpaRepository<OtpCode, UUID> {
    Optional<OtpCode> findTopByEmailAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(
        String email, String code, OtpType type);

    void deleteByEmailAndType(String email, OtpType type);

    void deleteByExpiresAtBefore(LocalDateTime now);
}
