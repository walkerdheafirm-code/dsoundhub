package com.dsoundhub.auth_service.repository;

import com.dsoundhub.auth_service.entity.AdminSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AdminSessionRepository extends JpaRepository<AdminSession, String> {
    void deleteByExpiresAtBefore(LocalDateTime now);
}
