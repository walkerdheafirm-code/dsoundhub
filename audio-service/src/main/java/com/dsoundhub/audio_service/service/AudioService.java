package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.dto.SongRequest;
import com.dsoundhub.audio_service.dto.SongResponse;
import com.dsoundhub.audio_service.entity.Song;
import com.dsoundhub.audio_service.repository.SongRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AudioService {

    private final SongRepository songRepository;
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.upload.dir}")
    private String uploadDir;

    public AudioService(SongRepository songRepository, DataSource dataSource) {
        this.songRepository = songRepository;
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

        Song saved = songRepository.save(song);
        return toResponse(saved);
    }

    public List<SongResponse> getAllSongs() {
        return songRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public List<SongResponse> getSongsByArtist(UUID artistId) {
        return songRepository.findByArtistId(artistId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Song getSongById(UUID songId) {
        return songRepository.findById(songId)
                .orElseThrow(() -> new RuntimeException("Song not found"));
    }

    @Transactional
    public void incrementPlayCount(UUID songId) {
        Song song = getSongById(songId);
        song.setTotalPlays(song.getTotalPlays() + 1);
        songRepository.save(song);
    }

    public List<Map<String, Object>> getArtists() {
        return jdbcTemplate.queryForList(
                "SELECT id, username FROM users WHERE role = 'ARTIST' ORDER BY username ASC"
        );
    }

    private SongResponse toResponse(Song song) {
        return new SongResponse(
                song.getId(),
                song.getArtistId(),
                song.getTitle(),
                song.getGenre(),
                song.getPrice(),
                song.getDurationSeconds(),
                song.getTotalPlays(),
                song.getCreatedAt()
        );
    }
}