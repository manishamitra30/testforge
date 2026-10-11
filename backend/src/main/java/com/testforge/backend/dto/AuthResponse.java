package com.testforge.backend.dto;

public record AuthResponse(
        String token,
        String tokenType,
        String username,
        String email,
        String role) {}