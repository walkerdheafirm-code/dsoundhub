package com.dsoundhub.audio_service.controller;

import com.dsoundhub.audio_service.entity.Royalty;
import com.dsoundhub.audio_service.service.RoyaltyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
    public ResponseEntity<List<Royalty>> getMyRoyalties(
            @AuthenticationPrincipal String userIdStr) {
        UUID artistId = UUID.fromString(userIdStr);
        return ResponseEntity.ok(royaltyService.getArtistRoyalties(artistId));
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
    public ResponseEntity<List<Royalty>> getAllPendingRoyalties() {
        return ResponseEntity.ok(royaltyService.getAllPendingRoyalties());
    }

    @PostMapping("/settle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> settleRoyalties() {
        royaltyService.settleAllPending();
        return ResponseEntity.ok(Map.of("message", "All pending royalties have been settled"));
    }
}