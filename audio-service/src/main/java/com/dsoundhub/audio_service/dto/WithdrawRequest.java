package com.dsoundhub.audio_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record WithdrawRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1", message = "Minimum withdrawal is 1")
    BigDecimal amount
) {}
