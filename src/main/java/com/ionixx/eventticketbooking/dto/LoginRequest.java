package com.ionixx.eventticketbooking.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @NotBlank(message = "password is required")
        // BCrypt only uses the first 72 bytes of a password.
        @Size(max = 72, message = "password must not exceed 72 characters")
        String password
) {
    public LoginRequest {
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    // Keeps the password out of logs and exception messages.
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=***]";
    }
}