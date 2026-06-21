package com.dsoundhub.auth_service.controller;

import com.dsoundhub.auth_service.dto.*;
import com.dsoundhub.auth_service.security.JwtProvider;
import com.dsoundhub.auth_service.service.AuthService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    public AuthController(AuthService authService, JwtProvider jwtProvider) {
        this.authService = authService;
        this.jwtProvider = jwtProvider;
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        authService.register(registerRequest);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registrasi berhasil! Silakan cek email untuk kode OTP.");
        response.put("email", registerRequest.email());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        TokenResponse tokenResponse = authService.login(loginRequest);
        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<?> logout(@RequestParam(value = "sessionId", required = false) String sessionId) {
        authService.logout(sessionId);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/verify-otp")
    public ResponseEntity<?> verifyOtp(@Valid @RequestBody VerifyOtpRequest request) {
        authService.verifyOtp(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Akun berhasil diverifikasi! Silakan login.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/resend-otp")
    public ResponseEntity<?> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        authService.resendOtp(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Kode OTP baru telah dikirim ke email Anda.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Kode OTP reset password telah dikirim ke email Anda.");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/auth/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        Map<String, String> response = new HashMap<>();
        response.put("message", "Password berhasil direset! Silakan login dengan password baru.");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/internal/validate-token")
    public ResponseEntity<?> validateToken(@RequestParam("token") String token) {
        if (jwtProvider.validateToken(token)) {
            Claims claims = jwtProvider.getClaims(token);
            Map<String, Object> response = new HashMap<>();
            response.put("valid", true);
            response.put("userId", claims.get("userId", String.class));
            response.put("username", claims.getSubject());
            response.put("role", claims.get("role", String.class));
            return ResponseEntity.ok(response);
        } else {
            Map<String, Object> response = new HashMap<>();
            response.put("valid", false);
            response.put("message", "Token expired or invalid");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }
}

