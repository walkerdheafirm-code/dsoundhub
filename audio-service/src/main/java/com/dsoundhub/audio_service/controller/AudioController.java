package com.dsoundhub.audio_service.controller;

import com.dsoundhub.audio_service.dto.SongRequest;
import com.dsoundhub.audio_service.dto.SongResponse;
import com.dsoundhub.audio_service.entity.Song;
import com.dsoundhub.audio_service.service.AudioService;
import com.dsoundhub.audio_service.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final TransactionService transactionService;
    private final AudioService audioService;

    public AudioController(AudioService audioService, TransactionService transactionService) {
        this.audioService = audioService;
        this.transactionService = transactionService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ARTIST')")
    public ResponseEntity<SongResponse> uploadSong(
            @RequestPart("metadata") @Valid SongRequest request,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) throws IOException {

        UUID artistId = UUID.fromString(userDetails.getUsername());
        SongResponse response = audioService.uploadSong(request, file, artistId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/songs")
    public ResponseEntity<List<SongResponse>> getAllSongs() {
        return ResponseEntity.ok(audioService.getAllSongs());
    }

    @GetMapping("/my-songs")
    @PreAuthorize("hasRole('ARTIST')")
    public ResponseEntity<List<SongResponse>> getMySongs(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID artistId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(audioService.getSongsByArtist(artistId));
    }

    @GetMapping("/preview/{id}")
    public ResponseEntity<Resource> previewSong(@PathVariable UUID id) throws IOException {
        Song song = audioService.getSongById(id);
        audioService.incrementPlayCount(id);

        Path filePath = Paths.get(song.getFilePath());
        byte[] allBytes = Files.readAllBytes(filePath);

        // Ambil 30 detik pertama — estimasi 128kbps = 480000 bytes per 30 detik
        int previewSize = Math.min(480000, allBytes.length);
        byte[] previewBytes = new byte[previewSize];
        System.arraycopy(allBytes, 0, previewBytes, 0, previewSize);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"preview.mp3\"")
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(new ByteArrayResource(previewBytes));
    }

    @PostMapping("/purchase/{id}")
    @PreAuthorize("hasRole('LISTENER')")
    public ResponseEntity<Map<String, String>> purchaseSong(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID listenerId = UUID.fromString(userDetails.getUsername());
        transactionService.purchaseSong(listenerId, id);
        return ResponseEntity.ok(Map.of("message", "Song purchased successfully"));
    }

    @GetMapping("/my-library")
    @PreAuthorize("hasRole('LISTENER')")
    public ResponseEntity<List<SongResponse>> getMyLibrary(
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID listenerId = UUID.fromString(userDetails.getUsername());
        List<SongResponse> songs = transactionService.getListenerLibrary(listenerId)
                .stream()
                .map(t -> new SongResponse(
                        t.getSong().getId(),
                        t.getSong().getArtistId(),
                        t.getSong().getTitle(),
                        t.getSong().getGenre(),
                        t.getSong().getPrice(),
                        t.getSong().getDurationSeconds(),
                        t.getSong().getTotalPlays(),
                        t.getSong().getCreatedAt()))
                .toList();
        return ResponseEntity.ok(songs);
    }

}