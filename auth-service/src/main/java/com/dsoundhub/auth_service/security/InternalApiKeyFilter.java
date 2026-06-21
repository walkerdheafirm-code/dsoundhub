package com.dsoundhub.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter yang mengamankan endpoint /internal/** dengan Internal API Key.
 * Hanya request yang menyertakan header "X-Internal-Key" dengan value
 * yang cocok dengan internal.api.key di application.properties yang diizinkan.
 */
@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String INTERNAL_KEY_HEADER = "X-Internal-Key";
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    private final String internalApiKey;

    public InternalApiKeyFilter(@Value("${internal.api.key}") String internalApiKey) {
        this.internalApiKey = internalApiKey;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        // Hanya intercept path /internal/**
        if (requestUri.startsWith(INTERNAL_PATH_PREFIX)) {
            String providedKey = request.getHeader(INTERNAL_KEY_HEADER);

            if (providedKey == null || !providedKey.equals(internalApiKey)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"error\":\"Forbidden: Invalid or missing internal API key\",\"status\":403}"
                );
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
