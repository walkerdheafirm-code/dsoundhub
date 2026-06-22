package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.entity.*;
import com.dsoundhub.audio_service.repository.RoyaltyRepository;
import com.dsoundhub.audio_service.repository.SongRepository;
import com.dsoundhub.audio_service.repository.TransactionRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RoyaltyRepository royaltyRepository;
    private final SongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;

    public TransactionService(TransactionRepository transactionRepository,
                               RoyaltyRepository royaltyRepository,
                               SongRepository songRepository,
                               DataSource dataSource) {
        this.transactionRepository = transactionRepository;
        this.royaltyRepository = royaltyRepository;
        this.songRepository = songRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Transactional
    public Transaction purchaseSong(UUID listenerId, UUID songId) {
        if (transactionRepository.existsByListenerIdAndSongId(listenerId, songId)) {
            throw new RuntimeException("You have already purchased this song");
        }

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        if (song.getStatus() != null && song.getStatus() != SongStatus.PUBLISHED) {
            throw new RuntimeException("Song is not available for purchase");
        }

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM users WHERE id = ?::uuid",
                BigDecimal.class,
                listenerId.toString()
        );

        if (balance == null || balance.compareTo(song.getPrice()) < 0) {
            throw new RuntimeException("Saldo tidak mencukupi. Silakan top-up terlebih dahulu.");
        }

        jdbcTemplate.update("UPDATE users SET balance = balance - ? WHERE id = ?::uuid",
                song.getPrice(), listenerId.toString());

        Transaction transaction = new Transaction();
        transaction.setListenerId(listenerId);
        transaction.setSong(song);
        transaction.setAmount(song.getPrice());
        transaction.setStatus(TransactionStatus.COMPLETED);

        Transaction saved = transactionRepository.save(transaction);

        BigDecimal royaltyAmount = song.getPrice()
                .multiply(BigDecimal.valueOf(0.70));

        Royalty royalty = new Royalty();
        royalty.setTransaction(saved);
        royalty.setArtistId(song.getArtistId());
        royalty.setAmount(royaltyAmount);
        royalty.setStatus(RoyaltyStatus.PENDING);

        royaltyRepository.save(royalty);

        return saved;
    }

    public List<Transaction> getListenerLibrary(UUID listenerId) {
        return transactionRepository.findByListenerId(listenerId);
    }
}
