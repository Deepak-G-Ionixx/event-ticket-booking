package com.ionixx.eventticketbooking.dto;

/**
 * A run of consecutive seat numbers, both ends included.
 *
 * {@code SeatRange(5, 9)} means seats 5, 6, 7, 8 and 9. A single seat is
 * written as {@code SeatRange(12, 12)}.
 */
public record SeatRange(int from, int to) {
}
