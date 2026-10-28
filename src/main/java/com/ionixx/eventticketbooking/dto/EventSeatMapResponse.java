package com.ionixx.eventticketbooking.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventSeatMapResponse(

        @JsonProperty("event_id")
        Integer eventId,

        @JsonProperty("total_seats")
        Integer totalSeats,

        @JsonProperty("booked_seats")
        List<Integer> bookedSeats,

        @JsonProperty("available_seats")
        List<Integer> availableSeats
) {
}
