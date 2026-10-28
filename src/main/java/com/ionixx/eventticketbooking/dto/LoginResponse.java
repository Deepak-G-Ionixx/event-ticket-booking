package com.ionixx.eventticketbooking.dto;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Integer userId,
        String email,
        String role
) {
}
