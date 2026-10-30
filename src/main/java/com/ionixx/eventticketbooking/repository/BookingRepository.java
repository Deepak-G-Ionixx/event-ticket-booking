package com.ionixx.eventticketbooking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Integer> {

    /**
     * All bookings of one event with a given status.
     * Used by tests to verify the database state after concurrent requests.
     */
    List<Booking> findAllByEvent_IdAndStatus(
            Integer eventId,
            BookingStatus status);

    /**
     * Loads a booking and its event together.
     * This helps avoid an extra query when checking event ownership.
     */
    @Query("""
            SELECT b
            FROM Booking b
            JOIN FETCH b.event
            WHERE b.id = :id
            """)
    Optional<Booking> findBookingWithEventById(
            @Param("id") Integer id);

    /**
     * Retrieves bookings for an event using database pagination.
     * Pageable supplies the page number, page size, and sorting.
     *
     * The event is deliberately NOT join-fetched: the caller has already
     * loaded the (owned) event in the same persistence context, so Hibernate
     * resolves every booking's event from memory without extra queries.
     */
    Page<Booking> findAllByEvent_Id(
            Integer eventId,
            Pageable pageable);

    /**
     * Retrieves bookings for an event and status using database pagination.
     * The status filter is applied before pagination.
     */
    Page<Booking> findAllByEvent_IdAndStatus(
            Integer eventId,
            BookingStatus status,
            Pageable pageable);

    /**
     * One page of the logged-in customer's own bookings.
     * The ownership filter is part of the query.
     *
     * A customer's bookings can belong to many different events, so the
     * to-one event association is fetched in the same SELECT (LEFT JOIN).
     * Fetching a to-one association is safe with pagination; only
     * collection fetches break LIMIT/OFFSET.
     */
    @EntityGraph(attributePaths = "event")
    Page<Booking> findAllByCustomerId(
            Integer customerId,
            Pageable pageable);

    /**
     * One page of the logged-in customer's own bookings with a given status.
     */
    @EntityGraph(attributePaths = "event")
    Page<Booking> findAllByCustomerIdAndStatus(
            Integer customerId,
            BookingStatus status,
            Pageable pageable);

    /**
     * Counts confirmed seats using the authenticated customer's database ID.
     * This avoids trusting a customer ID or email supplied by the client.
     */
    @Query("""
            SELECT COALESCE(SUM(b.seats), 0)
            FROM Booking b
            WHERE b.event.id = :eventId
              AND b.customerId = :customerId
              AND b.status = :status
            """)
    Long countConfirmedSeatsForCustomerId(
            @Param("eventId") Integer eventId,
            @Param("customerId") Integer customerId,
            @Param("status") BookingStatus status);

    /**
     * Booking count, seat count and revenue per status for one event,
     * calculated by the database in a single query.
     */
    @Query("""
            SELECT b.status AS bookingStatus,
                   COUNT(b) AS bookingCount,
                   SUM(b.seats) AS seatCount,
                   SUM(b.totalAmount) AS revenue
            FROM Booking b
            WHERE b.event.id = :eventId
            GROUP BY b.status
            """)
    List<BookingStatusTotals> summarizeByStatus(
            @Param("eventId") Integer eventId);
}
