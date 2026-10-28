package com.ionixx.eventticketbooking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Integer> {

    List<Booking> findAllByEvent_IdAndStatus(
            Integer eventId,
            BookingStatus status);

    List<Booking> findAllByEvent_IdOrderByBookedOnDescIdDesc(
            Integer eventId);

    List<Booking> findAllByEvent_IdAndStatusOrderByBookedOnDescIdDesc(
            Integer eventId,
            BookingStatus status);

    @Query("""
            SELECT COALESCE(SUM(b.seats), 0)
            FROM Booking b
            WHERE b.event.id = :eventId
              AND LOWER(b.customerEmail) = LOWER(:email)
              AND b.status = :status
            """)
    Long countConfirmedSeatsForCustomer(
            @Param("eventId") Integer eventId,
            @Param("email") String email,
            @Param("status") BookingStatus status);

    @Query("""
            SELECT b
            FROM Booking b
            JOIN FETCH b.event
            WHERE b.id = :id
            """)
    Optional<Booking> findBookingWithEventById(
            @Param("id") Integer id);
}
