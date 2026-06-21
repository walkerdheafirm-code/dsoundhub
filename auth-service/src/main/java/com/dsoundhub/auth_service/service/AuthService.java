package com.dsoundhub.auth_service.service;

import com.dsoundhub.auth_service.dto.*;
import com.dsoundhub.auth_service.entity.OtpCode;
import com.dsoundhub.auth_service.entity.OtpType;
import com.dsoundhub.auth_service.entity.Role;
import com.dsoundhub.auth_service.entity.User;
import com.dsoundhub.auth_service.repository.OtpCodeRepository;
import com.dsoundhub.auth_service.repository.UserRepository;
import com.dsoundhub.auth_service.security.JwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Random;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final SessionService sessionService;
    private final OtpCodeRepository otpCodeRepository;
    private final EmailService emailService;

    private final Random random = new Random();

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtProvider jwtProvider, SessionService sessionService,
                       OtpCodeRepository otpCodeRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
        this.sessionService = sessionService;
        this.otpCodeRepository = otpCodeRepository;
        this.emailService = emailService;
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
            admin.setIsVerified(true);
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
        user.setIsVerified(false);
        user.setBalance(BigDecimal.ZERO);

        userRepository.save(user);

        sendVerificationOtp(user.getEmail());
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

        if (!Boolean.TRUE.equals(user.getIsVerified())) {
            throw new RuntimeException("Akun belum diverifikasi. Silakan cek email Anda atau klik 'Konfirmasi Akun'.");
        }

        String token = jwtProvider.generateToken(user);

        String sessionId = null;
        if (user.getRole() == Role.ADMIN) {
            sessionId = sessionService.createAdminSession(user.getId().toString());
        }

        return new TokenResponse(token, user.getRole().name(), user.getUsername(), sessionId);
    }

    public void logout(String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            sessionService.destroyAdminSession(sessionId);
        }
    }

    @Transactional
    public void verifyOtp(VerifyOtpRequest request) {
        OtpCode otp = otpCodeRepository
                .findTopByEmailAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(
                        request.email(), request.code(), OtpType.VERIFICATION)
                .orElseThrow(() -> new RuntimeException("Kode OTP tidak valid"));

        if (otp.isExpired()) {
            throw new RuntimeException("Kode OTP sudah kadaluwarsa. Silakan minta kode baru.");
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsVerified(true);
        userRepository.save(user);
    }

    @Transactional
    public void resendOtp(ResendOtpRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Email tidak ditemukan"));

        if (Boolean.TRUE.equals(user.getIsVerified())) {
            throw new RuntimeException("Akun sudah terverifikasi");
        }

        sendVerificationOtp(request.email());
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        if (userRepository.findByEmail(request.email()).isEmpty()) {
            throw new RuntimeException("Email tidak ditemukan");
        }

        generateAndSendOtp(request.email(), OtpType.PASSWORD_RESET);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        OtpCode otp = otpCodeRepository
                .findTopByEmailAndCodeAndTypeAndUsedFalseOrderByCreatedAtDesc(
                        request.email(), request.code(), OtpType.PASSWORD_RESET)
                .orElseThrow(() -> new RuntimeException("Kode OTP tidak valid"));

        if (otp.isExpired()) {
            throw new RuntimeException("Kode OTP sudah kadaluwarsa");
        }

        otp.setUsed(true);
        otpCodeRepository.save(otp);

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private void sendVerificationOtp(String email) {
        otpCodeRepository.deleteByEmailAndType(email, OtpType.VERIFICATION);
        generateAndSendOtp(email, OtpType.VERIFICATION);
    }

    private void generateAndSendOtp(String email, OtpType type) {
        String code = String.format("%06d", random.nextInt(999999));
        OtpCode otp = new OtpCode(email, code, type);
        otpCodeRepository.save(otp);

        String label = type == OtpType.VERIFICATION ? "Verifikasi" : "Reset Password";
        emailService.sendOtp(email, code, label);
    }
}
