package com.ionixx.eventticketbooking.repository;

import java.math.BigDecimal;

import com.ionixx.eventticketbooking.entity.BookingStatus;

/**
 * One row of the aggregate query used by the event summary:
 * booking count, seat count and revenue per booking status.
 *
 * The database does the counting, so the summary no longer loads every
 * booking of the event into memory.
 */
public interface BookingStatusTotals {

    BookingStatus getBookingStatus();

    Long getBookingCount();

    Long getSeatCount();

    BigDecimal getRevenue();
}
