package com.dsoundhub.audio_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record SongRequest(
    @NotBlank(message = "Title is required")
    String title,

    String genre,

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.0", message = "Price must be positive")
    BigDecimal price,

    Integer durationSeconds
) {}