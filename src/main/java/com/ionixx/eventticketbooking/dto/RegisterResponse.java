
package com.ionixx.eventticketbooking.dto;

/**
 * Response returned after a customer account is created.
 *
 * Passwords and password hashes must never be included in API responses.
 */
public record RegisterResponse(
        Integer userId,
        String name,
        String email,
        String role,
        String message
) {
}
