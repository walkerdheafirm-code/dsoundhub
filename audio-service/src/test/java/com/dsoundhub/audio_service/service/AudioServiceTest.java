package com.dsoundhub.audio_service.service;

import com.dsoundhub.audio_service.entity.Song;
import com.dsoundhub.audio_service.entity.SongStatus;
import com.dsoundhub.audio_service.repository.SongRepository;
import com.dsoundhub.audio_service.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AudioServiceTest {

    private SongRepository songRepository;
    private TransactionRepository transactionRepository;
    private AudioService audioService;

    @BeforeEach
    void setUp() {
        songRepository = mock(SongRepository.class);
        transactionRepository = mock(TransactionRepository.class);
        DataSource dataSource = mock(DataSource.class);
        audioService = new AudioService(songRepository, transactionRepository, dataSource);
        when(songRepository.save(any(Song.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void ownerCanUnpublishAndRepublishSong() {
        UUID artistId = UUID.randomUUID();
        Song song = song(artistId, SongStatus.PUBLISHED);
        when(songRepository.findById(song.getId())).thenReturn(Optional.of(song));

        assertEquals(SongStatus.UNPUBLISHED,
                audioService.setPublication(song.getId(), artistId, false).status());
        assertEquals(SongStatus.PUBLISHED,
                audioService.setPublication(song.getId(), artistId, true).status());
    }

    @Test
    void otherArtistCannotManageSong() {
        Song song = song(UUID.randomUUID(), SongStatus.PUBLISHED);
        when(songRepository.findById(song.getId())).thenReturn(Optional.of(song));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> audioService.setPublication(song.getId(), UUID.randomUUID(), false));

        assertEquals("You can only manage your own songs", error.getMessage());
        verify(songRepository, never()).save(any());
    }

    @Test
    void purchasedSongUsesSoftDeleteAndKeepsFile(@TempDir Path tempDir) throws Exception {
        UUID artistId = UUID.randomUUID();
        Path audioFile = Files.writeString(tempDir.resolve("song.mp3"), "audio");
        Song song = song(artistId, SongStatus.PUBLISHED);
        song.setFilePath(audioFile.toString());
        when(songRepository.findById(song.getId())).thenReturn(Optional.of(song));
        when(transactionRepository.existsBySongId(song.getId())).thenReturn(true);

        assertFalse(audioService.deleteSong(song.getId(), artistId));
        assertEquals(SongStatus.DELETED, song.getStatus());
        assertTrue(Files.exists(audioFile));
        verify(songRepository).save(song);
        verify(songRepository, never()).delete(song);
    }

    @Test
    void unpurchasedSongIsDeletedWithItsFile(@TempDir Path tempDir) throws Exception {
        UUID artistId = UUID.randomUUID();
        Path audioFile = Files.writeString(tempDir.resolve("song.mp3"), "audio");
        Song song = song(artistId, SongStatus.PUBLISHED);
        song.setFilePath(audioFile.toString());
        when(songRepository.findById(song.getId())).thenReturn(Optional.of(song));
        when(transactionRepository.existsBySongId(song.getId())).thenReturn(false);

        assertTrue(audioService.deleteSong(song.getId(), artistId));
        assertFalse(Files.exists(audioFile));
        verify(songRepository).delete(song);
    }

    @Test
    void privateSongCanOnlyBePreviewedByOwnerOrBuyer() {
        UUID artistId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID strangerId = UUID.randomUUID();
        Song song = song(artistId, SongStatus.UNPUBLISHED);
        when(transactionRepository.existsByListenerIdAndSongId(buyerId, song.getId())).thenReturn(true);

        assertTrue(audioService.canPreview(song, artistId));
        assertTrue(audioService.canPreview(song, buyerId));
        assertFalse(audioService.canPreview(song, strangerId));
    }

    @Test
    void deletedSongCanStillBePreviewedByBuyer() {
        UUID buyerId = UUID.randomUUID();
        Song song = song(UUID.randomUUID(), SongStatus.DELETED);
        when(transactionRepository.existsByListenerIdAndSongId(buyerId, song.getId())).thenReturn(true);

        assertTrue(audioService.canPreview(song, buyerId));
        assertFalse(audioService.canPreview(song, UUID.randomUUID()));
    }

    private Song song(UUID artistId, SongStatus status) {
        Song song = new Song();
        song.setId(UUID.randomUUID());
        song.setArtistId(artistId);
        song.setTitle("Test Song");
        song.setStatus(status);
        song.setTotalPlays(0);
        return song;
    }
}
