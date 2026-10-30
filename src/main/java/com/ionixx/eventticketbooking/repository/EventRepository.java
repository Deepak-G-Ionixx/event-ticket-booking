package com.ionixx.eventticketbooking.repository;

import java.time.LocalDate;
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

public interface EventRepository extends JpaRepository<Event, Integer> {

 boolean existsByNameAndEventDate(String name, LocalDate eventDate);

 // Used when updating an event: another event may not use the same name and date.
 boolean existsByNameAndEventDateAndIdNot(
         String name,
         LocalDate eventDate,
         Integer id);

 /**
  * Returns only events owned by the specified organiser.
  */
 List<Event> findAllByOrganiserId(Integer organiserId);

 /**
  * One page of the organiser's own events.
  * The ownership filter is part of the query, so no page can contain
  * another organiser's event.
  */
 Page<Event> findAllByOrganiserId(Integer organiserId, Pageable pageable);

 /**
  * Owner-scoped lookup: finds the event only when it belongs to the
  * given organiser. Used for read-only owner operations
  * (booking list, summary).
  */
 Optional<Event> findByIdAndOrganiserId(Integer id, Integer organiserId);

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
            """)
 Optional<Event> findOwnedByIdForUpdate(
         @Param("id") Integer id,
         @Param("organiserId") Integer organiserId);

 /**
  * Loads an event with a shared lock.
  *
  * Many bookings can hold the shared lock at the same time, but an
  * organiser's update or delete (which needs the exclusive lock) waits
  * until those bookings have committed. This keeps the seat-range check
  * of a booking valid until the booking is saved.
  */
 @Lock(LockModeType.PESSIMISTIC_READ)
 @Query("""
            SELECT e
            FROM Event e
            WHERE e.id = :id
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
}