package com.ionixx.eventticketbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.ionixx.eventticketbooking.entity.EventCategory;

/**
 * An event a customer can book, as shown in GET /api/events.
 *
 * Contains what a customer needs before booking: what, where, when,
 * the price, how many seats are still free and which seat numbers they are.
 * Internal fields such as the organiser id and the event log id are not
 * exposed.
 *
 * The free seats are returned as ranges, for example
 * [{from: 1, to: 4}, {from: 7, to: 200}] means seats 1-4 and 7-200 are free.
 * This keeps the response small even for a hall with 10,000 seats.
 */
public record AvailableEventResponse(
        Integer id,
        String name,
        String venue,
        EventCategory category,
        LocalDate eventDate,
        BigDecimal basePrice,
        Integer totalSeats,
        Integer availableSeats,
        List<SeatRange> availableSeatRanges
) {
}
