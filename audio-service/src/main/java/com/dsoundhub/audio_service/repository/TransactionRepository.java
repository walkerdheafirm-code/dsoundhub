package com.dsoundhub.audio_service.repository;

import com.dsoundhub.audio_service.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    boolean existsByListenerIdAndSongId(UUID listenerId, UUID songId);
    boolean existsBySongId(UUID songId);
    List<Transaction> findByListenerId(UUID listenerId);
}
