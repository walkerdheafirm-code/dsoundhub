package com.dsoundhub.auth_service.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service untuk mengelola session Admin di Redis dan flag ban user.
 *
 * Key patterns:
 *   - admin:session:{sessionId}  → value: adminId (TTL 1 jam)
 *   - user:ban:{userId}          → value: "true" (tanpa TTL, permanen hingga unban)
 */
@Service
public class SessionService {

    private static final String ADMIN_SESSION_PREFIX = "admin:session:";
    private static final String USER_BAN_PREFIX = "user:ban:";
    private static final Duration SESSION_TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public SessionService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ========================
    // Admin Session Management
    // ========================

    /**
     * Membuat session baru untuk Admin di Redis.
     * @param adminId UUID dari admin yang login
     * @return sessionId yang dihasilkan
     */
    public String createAdminSession(String adminId) {
        String sessionId = UUID.randomUUID().toString();
        String key = ADMIN_SESSION_PREFIX + sessionId;

        // Simpan adminId dan timestamp di Redis dengan TTL 1 jam
        redisTemplate.opsForHash().put(key, "adminId", adminId);
        redisTemplate.opsForHash().put(key, "sessionCreatedAt", LocalDateTime.now().toString());
        redisTemplate.expire(key, SESSION_TTL);

        return sessionId;
    }

    /**
     * Validasi apakah session Admin masih aktif.
     * @param sessionId ID session yang akan divalidasi
     * @return adminId jika session valid, null jika tidak
     */
    public String validateAdminSession(String sessionId) {
        String key = ADMIN_SESSION_PREFIX + sessionId;
        Object adminId = redisTemplate.opsForHash().get(key, "adminId");
        return adminId != null ? adminId.toString() : null;
    }

    /**
     * Menghapus session Admin dari Redis (logout).
     * @param sessionId ID session yang akan dihapus
     */
    public void destroyAdminSession(String sessionId) {
        String key = ADMIN_SESSION_PREFIX + sessionId;
        redisTemplate.delete(key);
    }

    // ========================
    // Ban / Unban Management
    // ========================

    /**
     * Ban user — tulis flag ke Redis.
     * @param userId UUID user yang akan di-ban
     */
    public void banUser(String userId) {
        String key = USER_BAN_PREFIX + userId;
        redisTemplate.opsForValue().set(key, "true");
    }

    /**
     * Unban user — hapus flag dari Redis.
     * @param userId UUID user yang akan di-unban
     */
    public void unbanUser(String userId) {
        String key = USER_BAN_PREFIX + userId;
        redisTemplate.delete(key);
    }

    /**
     * Cek apakah user sedang di-ban.
     * @param userId UUID user yang akan dicek
     * @return true jika user di-ban
     */
    public boolean isUserBanned(String userId) {
        String key = USER_BAN_PREFIX + userId;
        return Boolean.TRUE.toString().equals(redisTemplate.opsForValue().get(key));
    }
}
