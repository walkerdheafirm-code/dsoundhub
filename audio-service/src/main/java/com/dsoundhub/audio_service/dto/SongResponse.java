package com.dsoundhub.audio_service.dto;

import com.dsoundhub.audio_service.entity.SongStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record SongResponse(
    UUID id,
    UUID artistId,
    String title,
    String genre,
    BigDecimal price,
    Integer durationSeconds,
    Integer totalPlays,
    SongStatus status,
    LocalDateTime createdAt
) {}
