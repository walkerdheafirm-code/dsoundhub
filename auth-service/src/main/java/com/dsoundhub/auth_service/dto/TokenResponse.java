package com.dsoundhub.auth_service.dto;

public record TokenResponse(
    String token,
    String role,
    String username
) {}
