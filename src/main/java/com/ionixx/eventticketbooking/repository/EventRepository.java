package com.ionixx.eventticketbooking.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ionixx.eventticketbooking.entity.Event;

/**
 * Soft delete: a deleted event keeps its row with active = false.
 *
 * Every lookup used by the services filters on active = true, so a deleted
 * event behaves exactly like a missing one (HTTP 404). The inherited
 * findById / existsById do NOT filter and are only meant for tests and
 * internal checks of the raw row.
 */
public interface EventRepository extends JpaRepository<Event, Integer> {

 // Active event by id (event details, seat map).
 Optional<Event> findByIdAndActiveTrue(Integer id);

 // Distinguishes "exists but not yours" (403) from "missing or deleted" (404).
 boolean existsByIdAndActiveTrue(Integer id);

 // Duplicate check when creating. Deleted events do not block the name/date.
 boolean existsByNameAndEventDateAndActiveTrue(String name, LocalDate eventDate);

 // Duplicate check when updating: another active event may not use the same name and date.
 boolean existsByNameAndEventDateAndIdNotAndActiveTrue(
         String name,
         LocalDate eventDate,
         Integer id);

 /**
  * Returns every event row owned by the specified organiser,
  * including deleted ones (used by tests that inspect the raw data).
  */
 List<Event> findAllByOrganiserId(Integer organiserId);

 /**
  * One page of the organiser's own active events.
  * The ownership filter is part of the query, so no page can contain
  * another organiser's event.
  */
 Page<Event> findAllByOrganiserIdAndActiveTrue(Integer organiserId, Pageable pageable);

 /**
  * Events customers can still book: active and dated today or later.
  */
 Page<Event> findAllByActiveTrueAndEventDateGreaterThanEqual(
         LocalDate fromDate,
         Pageable pageable);

 /**
  * Owner-scoped lookup: finds the active event only when it belongs to the
  * given organiser. Used for read-only owner operations
  * (booking list, summary).
  */
 Optional<Event> findByIdAndOrganiserIdAndActiveTrue(Integer id, Integer organiserId);

 /**
  * Owner-scoped lookup that also locks the event row until the
  * transaction ends. Used by update and delete so that no booking can be
  * created for the event while it is being shrunk or removed.
  */
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("""
            SELECT e
            FROM Event e
            WHERE e.id = :id
              AND e.organiserId = :organiserId
              AND e.active = true
            """)
 Optional<Event> findOwnedByIdForUpdate(
         @Param("id") Integer id,
         @Param("organiserId") Integer organiserId);

 /**
  * Loads an active event with a shared lock.
  *
  * Many bookings can hold the shared lock at the same time, but an
  * organiser's update or delete (which needs the exclusive lock) waits
  * until those bookings have committed. This keeps the seat-range check
  * of a booking valid until the booking is saved. A booking that waited
  * on a delete re-checks active = true and gets 404.
  */
 @Lock(LockModeType.PESSIMISTIC_READ)
 @Query("""
            SELECT e
            FROM Event e
            WHERE e.id = :id
              AND e.active = true
            """)
 Optional<Event> findByIdForShare(@Param("id") Integer id);

 // True when at least one booking (confirmed or rejected) refers to the event.
 @Query("""
            SELECT COUNT(b) > 0
            FROM Booking b
            WHERE b.event.id = :eventId
            """)
 boolean hasBookings(@Param("eventId") Integer eventId);

 // Highest seat number reserved for the event, or 0 when nothing is reserved.
 @Query("""
            SELECT COALESCE(MAX(bs.seatNumber), 0)
            FROM BookingSeat bs
            WHERE bs.event.id = :eventId
            """)
 Integer findHighestReservedSeat(@Param("eventId") Integer eventId);

 /**
  * Confirmed (booked) seat numbers for several events in one query.
  * Each row is [eventId (Integer), seatNumber (Integer)], ordered by event
  * and then seat number. Events with no confirmed seats are simply absent.
  * Rejected bookings never block a seat.
  */
 @Query("""
            SELECT bs.event.id, bs.seatNumber
            FROM BookingSeat bs
            WHERE bs.event.id IN :eventIds
              AND bs.booking.status = com.ionixx.eventticketbooking.entity.BookingStatus.CONFIRMED
            ORDER BY bs.event.id, bs.seatNumber
            """)
 List<Object[]> findConfirmedSeatNumbersByEventIds(
         @Param("eventIds") Collection<Integer> eventIds);
}
