package com.dsoundhub.auth_service.service;

import com.dsoundhub.auth_service.entity.AdminSession;
import com.dsoundhub.auth_service.repository.AdminSessionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class SessionService {

    private final AdminSessionRepository adminSessionRepository;

    public SessionService(AdminSessionRepository adminSessionRepository) {
        this.adminSessionRepository = adminSessionRepository;
    }

    public String createAdminSession(String adminId) {
        String sessionId = UUID.randomUUID().toString();
        AdminSession session = new AdminSession(
            sessionId,
            adminId,
            LocalDateTime.now(),
            LocalDateTime.now().plusHours(1)
        );
        adminSessionRepository.save(session);
        return sessionId;
    }

    public String validateAdminSession(String sessionId) {
        return adminSessionRepository.findById(sessionId)
            .filter(s -> !s.isExpired())
            .map(AdminSession::getAdminId)
            .orElse(null);
    }

    public void destroyAdminSession(String sessionId) {
        adminSessionRepository.deleteById(sessionId);
    }
}
