package com.dsoundhub.audio_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Component
public class BanCheckInterceptor implements HandlerInterceptor {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public BanCheckInterceptor(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return true;
        }

        Object userIdObj = authentication.getPrincipal();
        if (userIdObj == null) {
            return true;
        }

        String userId = userIdObj.toString();

        Boolean banned = jdbcTemplate.queryForObject(
            "SELECT is_banned FROM users WHERE id = ?::uuid",
            Boolean.class,
            userId
        );

        if (Boolean.TRUE.equals(banned)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");

            Map<String, Object> errorBody = new HashMap<>();
            errorBody.put("error", "Account suspended");
            errorBody.put("message", "Your account has been suspended by an administrator. Please contact support.");
            errorBody.put("status", 403);

            response.getWriter().write(objectMapper.writeValueAsString(errorBody));
            return false;
        }

        return true;
    }
}
