package com.ionixx.eventticketbooking.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ionixx.eventticketbooking.entity.BookingSeat;

public interface BookingSeatRepository extends JpaRepository<BookingSeat, Long> {

    /**
     * Returns the seat numbers reserved by confirmed bookings for an event.
     * Rejected bookings must not make seats unavailable.
     */
    @Query("""
            SELECT bs.seatNumber
            FROM BookingSeat bs
            WHERE bs.event.id = :eventId
              AND bs.booking.status = com.ionixx.eventticketbooking.entity.BookingStatus.CONFIRMED
            ORDER BY bs.seatNumber
            """)
    List<Integer> findConfirmedSeatNumbersByEventId(
            @Param("eventId") Integer eventId);

    /**
     * Counts seats reserved by confirmed bookings for an event.
     */
    @Query("""
            SELECT COUNT(bs.id)
            FROM BookingSeat bs
            WHERE bs.event.id = :eventId
              AND bs.booking.status = com.ionixx.eventticketbooking.entity.BookingStatus.CONFIRMED
            """)
    long countConfirmedSeatsByEventId(
            @Param("eventId") Integer eventId);
}
