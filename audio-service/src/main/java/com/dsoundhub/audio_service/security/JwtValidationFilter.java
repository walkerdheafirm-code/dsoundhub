package com.dsoundhub.audio_service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

/**
 * Filter JWT untuk audio-service.
 * Memvalidasi JWT dengan dua cara:
 * 1. Validasi lokal menggunakan jwt.secret
 * 2. Validasi remote ke auth-service via /internal/validate-token,
 *    menyertakan header X-Internal-Key untuk autentikasi service-to-service.
 *
 * Menyimpan userId di authentication principal agar BanCheckInterceptor dapat
 * membacanya.
 */
@Component
public class JwtValidationFilter extends OncePerRequestFilter {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";

    private final SecretKey key;
    private final String authServiceUrl;
    private final String internalApiKey;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public JwtValidationFilter(
            @Value("${jwt.secret}") String secret,
            @Value("${auth.service.url}") String authServiceUrl,
            @Value("${internal.api.key}") String internalApiKey) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.authServiceUrl = authServiceUrl;
        this.internalApiKey = internalApiKey;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = resolveToken(request);

        if (token != null) {
            try {
                // Validasi lokal terlebih dahulu (cepat, tanpa network call)
                Claims claims = Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                String userId = claims.get("userId", String.class);
                String role = claims.get("role", String.class);

                // Validasi remote ke auth-service dengan X-Internal-Key header
                boolean remoteValid = validateTokenRemotely(token);

                if (remoteValid) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    userId, null,
                                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role)));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } else {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"Token is no longer valid\",\"status\":401}");
                    return;
                }

            } catch (ExpiredJwtException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token expired\",\"status\":401}");
                return;
            } catch (JwtException | IllegalArgumentException e) {
                // Token invalid, lanjutkan tanpa authentication
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Memanggil /internal/validate-token di auth-service dengan header X-Internal-Key.
     * Ini memastikan token masih valid di sisi auth-service (belum di-logout, user tidak banned, dll).
     */
    private boolean validateTokenRemotely(String token) {
    try {
        String encodedToken = java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(authServiceUrl + "/internal/validate-token?token=" + encodedToken))
                .header(INTERNAL_KEY_HEADER, internalApiKey)
                .GET()
                .build();

        HttpResponse<String> httpResponse = httpClient.send(httpRequest,
                HttpResponse.BodyHandlers.ofString());

        if (httpResponse.statusCode() == 200) {
            Map<?, ?> body = objectMapper.readValue(httpResponse.body(), Map.class);
            Object valid = body.get("valid");
            return Boolean.TRUE.equals(valid);
        }
        return false;
    } catch (Exception e) {
        logger.warn("Failed to validate token remotely: " + e.getMessage());
        return true;
    }
}

    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
