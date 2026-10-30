package com.ionixx.eventticketbooking.dto;

import java.util.Locale;

import com.ionixx.eventticketbooking.entity.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body used when a new user registers.
 *
 * role is optional: "CUSTOMER" or "ORGANISER". When it is left out, the account
 * is created as a CUSTOMER. Any other value is rejected with 400.
 */
public record RegisterRequest(

        @NotBlank(message = "name is required")
        @Size(min = 3, max = 100, message = "name must contain 3 to 100 characters")
        String name,

        @NotBlank(message = "email is required")
        @Email(message = "email must be a valid email address")
        @Size(max = 254, message = "email must not exceed 254 characters")
        String email,

        @NotBlank(message = "password is required")
        @Size(min = 8, max = 72, message = "password must contain 8 to 72 characters")
        String password,

        Role role
) {
    public RegisterRequest {
        // Normalize email so Deepak@Example.com and deepak@example.com
        // are treated as the same email address by the application.
        if (email != null) {
            email = email.trim().toLowerCase(Locale.ROOT);
        }

        // Remove accidental spaces at the beginning and end of the name.
        if (name != null) {
            name = name.trim();
        }

        // Leaving the role out means a normal customer account.
        if (role == null) {
            role = Role.CUSTOMER;
        }
    }

    // Avoid exposing the raw password if this request is ever logged.
    @Override
    public String toString() {
        return "RegisterRequest[name=" + name
                + ", email=" + email
                + ", password=***"
                + ", role=" + role + "]";
    }
}
