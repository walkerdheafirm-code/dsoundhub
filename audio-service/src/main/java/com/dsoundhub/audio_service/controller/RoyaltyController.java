package com.dsoundhub.audio_service.controller;

import com.dsoundhub.audio_service.entity.Royalty;
import com.dsoundhub.audio_service.entity.RoyaltyStatus;
import com.dsoundhub.audio_service.service.RoyaltyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/royalties")
public class RoyaltyController {

    private final RoyaltyService royaltyService;

    public RoyaltyController(RoyaltyService royaltyService) {
        this.royaltyService = royaltyService;
    }

    @GetMapping("/my")
    @PreAuthorize("hasRole('ARTIST')")
    public ResponseEntity<List<Map<String, Object>>> getMyRoyalties(
            @AuthenticationPrincipal String userIdStr) {
        UUID artistId = UUID.fromString(userIdStr);
        List<Royalty> royalties = royaltyService.getArtistRoyalties(artistId);
        List<Map<String, Object>> response = royalties.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId().toString());
            m.put("transactionId", r.getTransaction() != null ? r.getTransaction().getId().toString() : null);
            m.put("amount", r.getAmount());
            m.put("status", r.getStatus().name());
            m.put("settledAt", r.getSettledAt() != null ? r.getSettledAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/my/total")
    @PreAuthorize("hasRole('ARTIST')")
    public ResponseEntity<Map<String, BigDecimal>> getMyTotalRoyalty(
            @AuthenticationPrincipal String userIdStr) {
        UUID artistId = UUID.fromString(userIdStr);
        BigDecimal total = royaltyService.getTotalRoyaltyByArtist(artistId);
        return ResponseEntity.ok(Map.of("totalRoyalty", total));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllPendingRoyalties() {
        List<Royalty> royalties = royaltyService.getAllPendingRoyalties();
        List<Map<String, Object>> response = royalties.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId().toString());
            m.put("transactionId", r.getTransaction() != null ? r.getTransaction().getId().toString() : null);
            m.put("artistId", r.getArtistId().toString());
            m.put("amount", r.getAmount());
            m.put("status", r.getStatus().name());
            m.put("settledAt", r.getSettledAt() != null ? r.getSettledAt().toString() : null);
            return m;
        }).toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/settle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> settleRoyalties() {
        royaltyService.settleAllPending();
        return ResponseEntity.ok(Map.of("message", "All pending royalties have been settled"));
    }
}
