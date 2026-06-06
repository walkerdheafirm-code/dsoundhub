package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.entity.*;
import com.dsoundhub.audio_service.repository.RoyaltyRepository;
import com.dsoundhub.audio_service.repository.SongRepository;
import com.dsoundhub.audio_service.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final RoyaltyRepository royaltyRepository;
    private final SongRepository songRepository;

    public TransactionService(TransactionRepository transactionRepository,
                               RoyaltyRepository royaltyRepository,
                               SongRepository songRepository) {
        this.transactionRepository = transactionRepository;
        this.royaltyRepository = royaltyRepository;
        this.songRepository = songRepository;
    }

    @Transactional
    public Transaction purchaseSong(UUID listenerId, UUID songId) {
        // Cek duplikat pembelian
        if (transactionRepository.existsByListenerIdAndSongId(listenerId, songId)) {
            throw new RuntimeException("You have already purchased this song");
        }

        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));

        // Buat transaksi
        Transaction transaction = new Transaction();
        transaction.setListenerId(listenerId);
        transaction.setSong(song);
        transaction.setAmount(song.getPrice());
        transaction.setStatus(TransactionStatus.COMPLETED);

        Transaction saved = transactionRepository.save(transaction);

        // Hitung royalti — 70% ke artist
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