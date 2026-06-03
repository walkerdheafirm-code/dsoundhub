package com.dsoundhub.audio_service.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Interceptor yang memeriksa apakah user yang sedang mengakses audio-service
 * telah di-ban oleh Admin.
 *
 * Alur:
 *   1. Setelah JwtValidationFilter berhasil mengautentikasi user,
 *      interceptor ini berjalan sebelum request masuk ke controller.
 *   2. Membaca userId dari Authentication principal (disimpan oleh JWT filter).
 *   3. Memeriksa Redis key "user:ban:{userId}".
 *   4. Jika key ada (bernilai "true"), request ditolak dengan HTTP 403 "Account suspended".
 *   5. Jika tidak ada, request dilanjutkan ke controller.
 *
 * Ini memungkinkan ban berlaku real-time tanpa menunggu JWT expired.
 */
@Component
public class BanCheckInterceptor implements HandlerInterceptor {

    private static final String USER_BAN_PREFIX = "user:ban:";
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BanCheckInterceptor(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Jika tidak ada authentication (endpoint public), lewati
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }

        // Ambil userId dari authentication details (di-set oleh JwtValidationFilter)
        Object userIdObj = authentication.getDetails();
        if (userIdObj == null) {
            return true;
        }

        String userId = userIdObj.toString();
        String banKey = USER_BAN_PREFIX + userId;

        // Cek Redis: apakah user di-ban?
        String banValue = redisTemplate.opsForValue().get(banKey);
        if ("true".equals(banValue)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Account suspended");
            errorBody.put("message", "Your account has been suspended by an administrator. Please contact support.");
            errorBody.put("status", 403);

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return false; // Hentikan request
        }

        return true; // User tidak di-ban, lanjutkan
    }
}
