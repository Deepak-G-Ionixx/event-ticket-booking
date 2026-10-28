package com.ionixx.eventticketbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ionixx.eventticketbooking.entity.EventCategory;

public record EventResponse(
        Integer id,
        String eventLogId,
        String name,
        String venue,
        EventCategory category,
        LocalDate eventDate,
        Integer totalSeats,
        BigDecimal basePrice
) {
}
