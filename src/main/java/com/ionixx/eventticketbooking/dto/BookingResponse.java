package com.ionixx.eventticketbooking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BookingResponse(
        Integer id,

        @JsonProperty("event_id")
        Integer eventId,

        @JsonProperty("customer_name")
        String customerName,

        @JsonProperty("customer_email")
        String customerEmail,

        @JsonProperty("seat_numbers")
        List<Integer> seatNumbers,

        Integer seats,

        @JsonProperty("price_per_seat")
        BigDecimal pricePerSeat,

        @JsonProperty("total_amount")
        BigDecimal totalAmount,

        String status,

        @JsonProperty("rejection_reason")
        String rejectionReason,

        @JsonProperty("booked_on")
        LocalDate bookedOn
) {}
