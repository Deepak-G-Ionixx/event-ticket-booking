package com.ionixx.eventticketbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ionixx.eventticketbooking.entity.EventCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEventRequest(
    @NotBlank(message = "Event name is required")
    @Size(min = 3, max = 100, message = "Event name must be between 3 and 100 characters")
    String name,

    @NotBlank(message = "Venue is required")
    @Size(min = 3, max = 100, message = "Venue must be between 3 and 100 characters")
    String venue,

    @NotNull(message = "Category is required")
    EventCategory category,

    @NotNull(message = "Event date is required")
    @Future(message = "Event date must be in the future")
    LocalDate eventDate,

    @NotNull(message = "Total seats is required")
    @Min(value = 10, message = "Total seats must be at least 10")
    @Max(value = 10000, message = "Total seats must not exceed 10000")
    Integer totalSeats,

    @NotNull(message = "Base price is required")
    @DecimalMin(value = "0.00", inclusive = false, message = "Base price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Base price must have at most 10 integer digits and 2 decimal places")
    BigDecimal basePrice
) {
    public CreateEventRequest {
        if (name != null) {
            name = name.trim();
        }
        if (venue != null) {
            venue = venue.trim();
        }
    }
}
