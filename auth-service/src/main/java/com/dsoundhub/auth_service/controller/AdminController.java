package com.dsoundhub.auth_service.controller;

import com.dsoundhub.auth_service.entity.User;
import com.dsoundhub.auth_service.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller untuk operasi Admin: daftar user, ban, dan unban.
 * Semua endpoint memerlukan role ADMIN (dikonfigurasi di SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    /**
     * GET /api/admin/users — Mendapatkan daftar semua user.
     */
    @GetMapping("/users")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<User> users = userService.getAllUsers();

        List<Map<String, Object>> response = users.stream().map(user -> {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("id", user.getId().toString());
            userMap.put("username", user.getUsername());
            userMap.put("email", user.getEmail());
            userMap.put("role", user.getRole().name());
            userMap.put("isBanned", user.getIsBanned());
            userMap.put("balance", user.getBalance());
            userMap.put("createdAt", user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            return userMap;
        }).toList();

        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/admin/users/{id}/ban — Ban user (tulis ke Redis + update DB).
     */
    @PutMapping("/users/{id}/ban")
    public ResponseEntity<?> banUser(@PathVariable("id") UUID userId) {
        User user = userService.banUser(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User '" + user.getUsername() + "' has been banned");
        response.put("userId", user.getId().toString());
        response.put("isBanned", true);
        return ResponseEntity.ok(response);
    }

    /**
     * PUT /api/admin/users/{id}/unban — Unban user (hapus dari Redis + update DB).
     */
    @PutMapping("/users/{id}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable("id") UUID userId) {
        User user = userService.unbanUser(userId);
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User '" + user.getUsername() + "' has been unbanned");
        response.put("userId", user.getId().toString());
        response.put("isBanned", false);
        return ResponseEntity.ok(response);
    }
}
