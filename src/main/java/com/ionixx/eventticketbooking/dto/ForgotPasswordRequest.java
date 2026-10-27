
package com.ionixx.eventticketbooking.dto;

import java.util.Locale;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body used to request a password-reset email.
 */
public record ForgotPasswordRequest(

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email
) {
    public ForgotPasswordRequest {
        // Normalize the email so lookups are case-insensitive
        // when the application stores emails in lowercase.
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }
    }

    // Keep the request safe if it is accidentally logged.
    @Override
    public String toString() {
        return "ForgotPasswordRequest[email=" + email + "]";
    }
}
