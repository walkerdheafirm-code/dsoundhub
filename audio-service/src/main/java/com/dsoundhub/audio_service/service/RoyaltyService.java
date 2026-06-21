package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.entity.Royalty;
import com.dsoundhub.audio_service.entity.RoyaltyStatus;
import com.dsoundhub.audio_service.repository.RoyaltyRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RoyaltyService {

    private final RoyaltyRepository royaltyRepository;
    private final JdbcTemplate jdbcTemplate;

    public RoyaltyService(RoyaltyRepository royaltyRepository, DataSource dataSource) {
        this.royaltyRepository = royaltyRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public List<Royalty> getArtistRoyalties(UUID artistId) {
        return royaltyRepository.findByArtistId(artistId);
    }

    public List<Royalty> getAllPendingRoyalties() {
        return royaltyRepository.findByStatus(RoyaltyStatus.PENDING);
    }

    @Transactional
    public void settleAllPending() {
        List<Royalty> pending = royaltyRepository.findByStatus(RoyaltyStatus.PENDING);
        pending.forEach(royalty -> {
            royalty.setStatus(RoyaltyStatus.SETTLED);
            royalty.setSettledAt(LocalDateTime.now());

            jdbcTemplate.update(
                "UPDATE users SET balance = COALESCE(balance, 0) + ? WHERE id = ?::uuid",
                royalty.getAmount(),
                royalty.getArtistId().toString()
            );
        });
        royaltyRepository.saveAll(pending);
    }

    public BigDecimal getTotalRoyaltyByArtist(UUID artistId) {
        return royaltyRepository.findByArtistId(artistId)
                .stream()
                .map(Royalty::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}