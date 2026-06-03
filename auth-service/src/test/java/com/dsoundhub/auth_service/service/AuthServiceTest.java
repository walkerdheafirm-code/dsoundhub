package com.dsoundhub.auth_service.service;

import com.dsoundhub.auth_service.dto.LoginRequest;
import com.dsoundhub.auth_service.dto.RegisterRequest;
import com.dsoundhub.auth_service.dto.TokenResponse;
import com.dsoundhub.auth_service.entity.Role;
import com.dsoundhub.auth_service.entity.User;
import com.dsoundhub.auth_service.repository.UserRepository;
import com.dsoundhub.auth_service.security.JwtProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
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
    private HttpServletRequest request;

    @Mock
    private HttpSession session;

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

        TokenResponse tokenResponse = authService.login(loginRequest, request);

        assertNotNull(tokenResponse);
        assertEquals("mocked_jwt_token", tokenResponse.token());
        assertEquals("LISTENER", tokenResponse.role());
        assertEquals("john_doe", tokenResponse.username());
        verify(request, never()).getSession(anyBoolean());
    }

    @Test
    void login_success_admin() {
        LoginRequest loginRequest = new LoginRequest("admin", "password");
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("admin");
        user.setPasswordHash("hashed_password");
        user.setRole(Role.ADMIN);
        user.setIsBanned(false);

        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "hashed_password")).thenReturn(true);
        when(jwtProvider.generateToken(user)).thenReturn("mocked_jwt_token");
        when(request.getSession(true)).thenReturn(session);

        TokenResponse tokenResponse = authService.login(loginRequest, request);

        assertNotNull(tokenResponse);
        assertEquals("mocked_jwt_token", tokenResponse.token());
        assertEquals("ADMIN", tokenResponse.role());
        verify(request, times(1)).getSession(true);
        verify(session, times(1)).setAttribute(eq("adminId"), anyString());
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

        RuntimeException exception = assertThrows(RuntimeException.class, () -> authService.login(loginRequest, request));
        assertEquals("Account suspended", exception.getMessage());
    }
}
