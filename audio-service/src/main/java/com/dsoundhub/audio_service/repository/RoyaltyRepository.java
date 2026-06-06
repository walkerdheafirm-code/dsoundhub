package com.dsoundhub.audio_service.repository;

import com.dsoundhub.audio_service.entity.Royalty;
import com.dsoundhub.audio_service.entity.RoyaltyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoyaltyRepository extends JpaRepository<Royalty, UUID> {
    List<Royalty> findByArtistId(UUID artistId);
    List<Royalty> findByStatus(RoyaltyStatus status);
}