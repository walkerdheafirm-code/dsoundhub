package com.dsoundhub.auth_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TopUpRequest(
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1", message = "Minimum top-up is 1")
    BigDecimal amount
) {}
