package com.ionixx.eventticketbooking.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/bookings.
 *
 * There is deliberately no customer id field. The booking always belongs to
 * the logged-in customer, and the stored email is the email of that account.
 *
 * customer_email is optional and kept only for backward compatibility with
 * existing clients. If it is sent, it must be a valid email address, but it
 * is never used to decide who owns the booking.
 */
public record CreateBookingRequest(

        @JsonProperty("event_id")
        @NotNull(message = "event_id is required")
        @Positive(message = "event_id must be a positive integer")
        Integer eventId,

        @JsonProperty("customer_name")
        @NotBlank(message = "customer_name is required")
        @Size(min = 3, max = 50,
                message = "customer_name must contain 3 to 50 characters")
        @Pattern(regexp = "^[A-Za-z ]+$",
                message = "customer_name must contain only letters and spaces")
        String customerName,

        @JsonProperty("customer_email")
        @Email(message = "customer_email must be a valid email address")
        @Size(max = 254,
                message = "customer_email must not exceed 254 characters")
        String customerEmail,

        @JsonProperty("seat_numbers")
        @NotEmpty(message = "seat_numbers must contain at least one seat")
        @Size(max = 6, message = "A booking can contain at most 6 seats")
        List<
                @NotNull(message = "Seat numbers cannot contain null")
                @Positive(message = "Seat numbers must be positive integers")
                        Integer> seatNumbers

) {
    public CreateBookingRequest {
        if (customerName != null) {
            customerName = customerName.trim();
        }

        if (customerEmail != null) {
            customerEmail = customerEmail.trim().toLowerCase(Locale.ROOT);
        }

        if (seatNumbers != null) {
            seatNumbers = Collections.unmodifiableList(
                    new ArrayList<>(seatNumbers));
        }
    }
}
