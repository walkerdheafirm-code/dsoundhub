package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.dto.SongRequest;
import com.dsoundhub.audio_service.dto.SongResponse;
import com.dsoundhub.audio_service.entity.Song;
import com.dsoundhub.audio_service.entity.SongStatus;
import com.dsoundhub.audio_service.repository.SongRepository;
import com.dsoundhub.audio_service.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AudioService {

    private final SongRepository songRepository;
    private final TransactionRepository transactionRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public AudioService(SongRepository songRepository,
                        TransactionRepository transactionRepository,
                        DataSource dataSource) {
        this.songRepository = songRepository;
        this.transactionRepository = transactionRepository;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Transactional
    public SongResponse uploadSong(SongRequest request, MultipartFile file, UUID artistId) throws IOException {
        // Buat direktori upload jika belum ada
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Simpan file
        String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Simpan metadata ke database
        Song song = new Song();
        song.setArtistId(artistId);
        song.setTitle(request.title());
        song.setGenre(request.genre());
        song.setPrice(request.price());
        song.setDurationSeconds(request.durationSeconds());
        song.setFilePath(filePath.toString());
        song.setTotalPlays(0);
        song.setStatus(SongStatus.PUBLISHED);

        Song saved = songRepository.save(song);
        return toResponse(saved);
    }

    public List<SongResponse> getAllSongs() {
        return songRepository.findAll()
                .stream()
                .filter(song -> effectiveStatus(song) == SongStatus.PUBLISHED)
                .sorted(Comparator.comparing(
                        Song::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public List<SongResponse> getSongsByArtist(UUID artistId) {
        return songRepository.findByArtistId(artistId)
                .stream()
                .filter(song -> effectiveStatus(song) != SongStatus.DELETED)
                .sorted(Comparator.comparing(
                        Song::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toResponse)
                .toList();
    }

    public Song getSongById(UUID songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));
    }

    @Transactional
    public SongResponse setPublication(UUID songId, UUID artistId, boolean published) {
        Song song = getOwnedSong(songId, artistId);
        if (song.getStatus() == SongStatus.DELETED) {
            throw new RuntimeException("Deleted song cannot be published again");
        }

        song.setStatus(published ? SongStatus.PUBLISHED : SongStatus.UNPUBLISHED);
        return toResponse(songRepository.save(song));
    }

    @Transactional
    public boolean deleteSong(UUID songId, UUID artistId) throws IOException {
        Song song = getOwnedSong(songId, artistId);
        if (song.getStatus() == SongStatus.DELETED) {
            throw new RuntimeException("Song has already been deleted");
        }

        if (transactionRepository.existsBySongId(songId)) {
            song.setStatus(SongStatus.DELETED);
            songRepository.save(song);
            return false;
        }

        songRepository.delete(song);
        if (song.getFilePath() != null) {
            Files.deleteIfExists(Paths.get(song.getFilePath()));
        }
        return true;
    }

    public boolean canPreview(Song song, UUID userId) {
        SongStatus status = effectiveStatus(song);
        if (status == SongStatus.PUBLISHED) {
            return true;
        }
        if (status == SongStatus.UNPUBLISHED && song.getArtistId().equals(userId)) {
            return true;
        }
        return transactionRepository.existsByListenerIdAndSongId(userId, song.getId());
    }

    @Transactional
    public void incrementPlayCount(UUID songId) {
        Song song = getSongById(songId);
        song.setTotalPlays((song.getTotalPlays() == null ? 0 : song.getTotalPlays()) + 1);
        songRepository.save(song);
    }

    public List<Map<String, Object>> getArtists() {
        return jdbcTemplate.queryForList(
                "SELECT id, username FROM users WHERE role = 'ARTIST' ORDER BY username ASC"
        );
    }

    public SongResponse toResponse(Song song) {
        return new SongResponse(
                song.getId(),
                song.getArtistId(),
                song.getTitle(),
                song.getGenre(),
                song.getPrice(),
                song.getDurationSeconds(),
                song.getTotalPlays(),
                effectiveStatus(song),
                song.getCreatedAt()
        );
    }

    private Song getOwnedSong(UUID songId, UUID artistId) {
        Song song = getSongById(songId);
        if (!song.getArtistId().equals(artistId)) {
            throw new AccessDeniedException("You can only manage your own songs");
        }
        return song;
    }

    private SongStatus effectiveStatus(Song song) {
        return song.getStatus() == null ? SongStatus.PUBLISHED : song.getStatus();
    }
}
