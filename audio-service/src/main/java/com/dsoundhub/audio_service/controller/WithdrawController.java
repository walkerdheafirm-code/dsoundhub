package com.dsoundhub.audio_service.controller;

import com.dsoundhub.audio_service.entity.Withdrawal;
import com.dsoundhub.audio_service.service.WithdrawService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/withdraw")
public class WithdrawController {

    private final WithdrawService withdrawService;

    public WithdrawController(WithdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> withdraw(
            @AuthenticationPrincipal String userIdStr,
            @RequestParam BigDecimal amount) {

        UUID userId = UUID.fromString(userIdStr);
        Withdrawal withdrawal = withdrawService.requestWithdraw(userId, amount);
        BigDecimal newBalance = withdrawService.getBalance(userId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Penarikan berhasil! Silakan cek email untuk notifikasi.");
        response.put("withdrawalId", withdrawal.getId().toString());
        response.put("amount", amount);
        response.put("balance", newBalance);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, Object>>> history(
            @AuthenticationPrincipal String userIdStr) {

        UUID userId = UUID.fromString(userIdStr);
        List<Withdrawal> withdrawals = withdrawService.getHistory(userId);
        List<Map<String, Object>> response = withdrawals.stream().map(w -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", w.getId().toString());
            m.put("amount", w.getAmount());
            m.put("status", w.getStatus().name());
            m.put("createdAt", w.getCreatedAt() != null ? w.getCreatedAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, BigDecimal>> balance(
            @AuthenticationPrincipal String userIdStr) {

        UUID userId = UUID.fromString(userIdStr);
        BigDecimal balance = withdrawService.getBalance(userId);
        return ResponseEntity.ok(Map.of("balance", balance));
    }
}
