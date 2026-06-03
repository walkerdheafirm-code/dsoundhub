package com.dsoundhub.auth_service.service;

import com.dsoundhub.auth_service.dto.LoginRequest;
import com.dsoundhub.auth_service.dto.RegisterRequest;
import com.dsoundhub.auth_service.dto.TokenResponse;
import com.dsoundhub.auth_service.entity.Role;
import com.dsoundhub.auth_service.entity.User;
import com.dsoundhub.auth_service.repository.UserRepository;
import com.dsoundhub.auth_service.security.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private SessionService sessionService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void register_success() {
        RegisterRequest registerRequest = new RegisterRequest("john_doe", "john@example.com", "password", "LISTENER");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password")).thenReturn("hashed_password");

        authService.register(registerRequest);

        verify(userRepository, times(1)).save(any(User.class));
    }

    @Test
    void register_usernameExists_throwsException() {
        RegisterRequest registerRequest = new RegisterRequest("john_doe", "john@example.com", "password", "LISTENER");

        when(userRepository.existsByUsername("john_doe")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.register(registerRequest));
        assertEquals("Username already exists", exception.getMessage());
    }

    @Test
    void register_adminRole_throwsException() {
        RegisterRequest registerRequest = new RegisterRequest("john_doe", "john@example.com", "password", "ADMIN");

        when(userRepository.existsByUsername("john_doe")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.register(registerRequest));
        assertEquals("Admin registration is not allowed", exception.getMessage());
    }

    @Test
    void login_success_listener() {
        LoginRequest loginRequest = new LoginRequest("john_doe", "password");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john_doe");
        user.setPasswordHash("hashed_password");
        user.setRole(Role.LISTENER);
        user.setIsBanned(false);

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);
        when(jwtProvider.generateToken(user)).thenReturn("mocked_jwt_token");

        TokenResponse tokenResponse = authService.login(loginRequest);

        assertNotNull(tokenResponse);
        assertEquals("mocked_jwt_token", tokenResponse.token());
        assertEquals("LISTENER", tokenResponse.role());
        assertEquals("john_doe", tokenResponse.username());
        assertNull(tokenResponse.sessionId()); // Listener tidak mendapat sessionId
        verify(sessionService, never()).createAdminSession(anyString());
    }

    @Test
    void login_success_admin() {
        LoginRequest loginRequest = new LoginRequest("admin", "password");
        User user = new User();
        UUID adminId = UUID.randomUUID();
        user.setId(adminId);
        user.setUsername("admin");
        user.setPasswordHash("hashed_password");
        user.setRole(Role.ADMIN);
        user.setIsBanned(false);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);
        when(jwtProvider.generateToken(user)).thenReturn("mocked_jwt_token");
        when(sessionService.createAdminSession(adminId.toString())).thenReturn("mocked_session_id");

        TokenResponse tokenResponse = authService.login(loginRequest);

        assertNotNull(tokenResponse);
        assertEquals("mocked_jwt_token", tokenResponse.token());
        assertEquals("ADMIN", tokenResponse.role());
        assertEquals("mocked_session_id", tokenResponse.sessionId()); // Admin mendapat sessionId
        verify(sessionService, times(1)).createAdminSession(adminId.toString());
    }

    @Test
    void login_bannedUser_throwsException() {
        LoginRequest loginRequest = new LoginRequest("john_doe", "password");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("john_doe");
        user.setPasswordHash("hashed_password");
        user.setRole(Role.LISTENER);
        user.setIsBanned(true);

        when(userRepository.findByUsername("john_doe")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.login(loginRequest));
        assertEquals("Account suspended", exception.getMessage());
    }

    @Test
    void logout_withSessionId_destroysSession() {
        authService.logout("some-session-id");
        verify(sessionService, times(1)).destroyAdminSession("some-session-id");
    }

    @Test
    void logout_withNullSessionId_doesNothing() {
        authService.logout(null);
        verify(sessionService, never()).destroyAdminSession(anyString());
    }

    @Test
    void logout_withBlankSessionId_doesNothing() {
        authService.logout("   ");
        verify(sessionService, never()).destroyAdminSession(anyString());
    }
}
