package com.dsoundhub.audio_service.repository;

import com.dsoundhub.audio_service.entity.Song;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SongRepository extends JpaRepository<Song, UUID> {
    List<Song> findByArtistId(UUID artistId);
}