package com.ionixx.eventticketbooking.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EventSummaryResponse(

        @JsonProperty("event_id")
        Integer eventId,

        @JsonProperty("event_name")
        String eventName,

        @JsonProperty("total_seats")
        Integer totalSeats,

        @JsonProperty("seats_sold")
        Integer seatsSold,

        @JsonProperty("seats_available")
        Integer seatsAvailable,

        @JsonProperty("confirmed_bookings")
        Integer confirmedBookings,

        @JsonProperty("rejected_bookings")
        Integer rejectedBookings,

        @JsonProperty("total_revenue")
        BigDecimal totalRevenue,

        @JsonProperty("occupancy_pct")
        BigDecimal occupancyPct,

        @JsonProperty("demand_band")
        String demandBand
) {
}
