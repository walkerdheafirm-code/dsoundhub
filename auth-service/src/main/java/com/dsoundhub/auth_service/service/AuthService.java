package com.dsoundhub.auth_service.service;

import com.dsoundhub.auth_service.dto.LoginRequest;
import com.dsoundhub.auth_service.dto.RegisterRequest;
import com.dsoundhub.auth_service.dto.TokenResponse;
import com.dsoundhub.auth_service.entity.Role;
import com.dsoundhub.auth_service.entity.User;
import com.dsoundhub.auth_service.repository.UserRepository;
import com.dsoundhub.auth_service.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SessionService sessionService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, SessionService sessionService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.sessionService = sessionService;
    }

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    @Transactional
    public void seedAdmin() {
        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@dsoundhub.com");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ADMIN);
            admin.setIsBanned(false);
            admin.setBalance(BigDecimal.ZERO);
            userRepository.save(admin);
            System.out.println("=================================================");
            System.out.println("Seeded default admin user: admin / admin123");
            System.out.println("=================================================");
        }
    }


    @Transactional
    public void register(RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.username())) {
            throw new RuntimeException("Username already exists");
        }

        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new RuntimeException("Email already exists");
        }

        Role role;
        try {
            role = Role.valueOf(registerRequest.role().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new RuntimeException("Invalid role specified");
        }

        if (role == Role.ADMIN) {
            throw new RuntimeException("Admin registration is not allowed");
        }

        User user = new User();
        user.setUsername(registerRequest.username());
        user.setEmail(registerRequest.email());
        user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));
        user.setRole(role);
        user.setIsBanned(false);
        user.setBalance(BigDecimal.ZERO);

        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByUsername(loginRequest.username())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!passwordEncoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }

        if (Boolean.TRUE.equals(user.getIsBanned())) {
            throw new RuntimeException("Account suspended");
        }

        String token = jwtProvider.generateToken(user);

        // Jika ADMIN, buat session Redis dan sertakan sessionId di response
        String sessionId = null;
        if (user.getRole() == Role.ADMIN) {
            sessionId = sessionService.createAdminSession(user.getId().toString());
        }

        return new TokenResponse(token, user.getRole().name(), user.getUsername(), sessionId);
    }

    /**
     * Logout — hapus session Admin dari Redis.
     * @param sessionId ID session admin yang akan dihapus
     */
    public void logout(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionService.destroyAdminSession(sessionId);
        }
    }
}
