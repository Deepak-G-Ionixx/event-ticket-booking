package com.ionixx.eventticketbooking.controller;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.exception.InvalidEventIdException;
import com.ionixx.eventticketbooking.service.BookingService;
import com.ionixx.eventticketbooking.service.EventService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;
    private final BookingService bookingService;

    public EventController(
            EventService eventService,
            BookingService bookingService) {
        this.eventService = eventService;
        this.bookingService = bookingService;
    }

    // POST /api/events
    // Creates an event and includes its location in the response headers.
    // The owner is the logged-in organiser; the body cannot choose one.
    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            HttpServletRequest httpRequest) {

        EventResponse response = eventService.createEvent(request);

        // Make the generated event log ID available to request logging.
        httpRequest.setAttribute("eventLogId", response.eventLogId());
        MDC.put("eventLogId", response.eventLogId());

        return ResponseEntity
                .created(ServletUriComponentsBuilder
                        .fromCurrentContextPath()
                        .path("/api/events/{id}")
                        .buildAndExpand(response.id())
                        .toUri())
                .body(response);
    }

    // GET /api/events/mine?page=0&size=10&sort=eventDate,asc
    // Only the logged-in organiser's own events. The literal path "mine"
    // is matched before the "/{id}" pattern below.
    @GetMapping("/mine")
    public ResponseEntity<Page<EventResponse>> getMyEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        return ResponseEntity.ok(eventService.getMyEvents(page, size, sort));
    }

    // PUT /api/events/{id}
    // Only the organiser who owns the event may change it.
    @PutMapping("/{id}")
    public ResponseEntity<EventResponse> updateEvent(
            @PathVariable String id,
            @Valid @RequestBody CreateEventRequest request) {

        return ResponseEntity.ok(
                eventService.updateEvent(parseEventId(id), request));
    }

    // DELETE /api/events/{id}
    // Only the organiser who owns the event may delete it, and only while
    // it has no bookings.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEvent(@PathVariable String id) {

        eventService.deleteEvent(parseEventId(id));

        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    // GET /api/events/{id}
    // Retrieves an event using its positive integer ID.
    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        int eventId = parseEventId(id);
        EventResponse response = eventService.getEventById(eventId);

        // Tag the request with the event's log ID for searchable logs.
        httpRequest.setAttribute("eventLogId", response.eventLogId());
        MDC.put("eventLogId", response.eventLogId());

        return ResponseEntity.ok(response);
    }

    // GET /api/events/{id}/bookings?page=0&size=10&status=CONFIRMED&sort=bookedOn,desc
    // Page numbering starts at zero. The service checks that the authenticated
    // organiser owns this event BEFORE validating paging or reading bookings.
    @GetMapping("/{id}/bookings")
    public ResponseEntity<Page<BookingResponse>> getEventBookingsPaginated(
            @PathVariable String id,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        int eventId = parseEventId(id);
        BookingStatus bookingStatus = parseBookingStatus(status);

        return ResponseEntity.ok(
                bookingService.getEventBookings(
                        eventId, bookingStatus, page, size, sort));
    }

    // A missing status means no filter.
    // Only the exact values CONFIRMED and REJECTED are accepted.
    private BookingStatus parseBookingStatus(String status) {
        if (status == null) {
            return null;
        }

        if ("CONFIRMED".equals(status)) {
            return BookingStatus.CONFIRMED;
        }

        if ("REJECTED".equals(status)) {
            return BookingStatus.REJECTED;
        }

        throw new IllegalArgumentException(
                "status must be CONFIRMED or REJECTED");
    }

    // GET /api/events/{id}/seats
    // Returns booked and available seats for an event.
    @GetMapping("/{id}/seats")
    public ResponseEntity<EventSeatMapResponse> getEventSeats(
            @PathVariable String id) {

        int eventId = parseEventId(id);

        return ResponseEntity.ok(
                bookingService.getEventSeatMap(eventId));
    }

    // GET /api/events/{id}/summary
    // Returns booking and revenue statistics for an event.
    @GetMapping("/{id}/summary")
    public ResponseEntity<EventSummaryResponse> getEventSummary(
            @PathVariable String id) {

        int eventId = parseEventId(id);

        return ResponseEntity.ok(
                bookingService.getEventSummary(eventId));
    }

    // Converts the URL ID to a positive integer.
    // Invalid values are rejected before calling the service.
    private int parseEventId(String id) {
        try {
            if (id == null || !id.matches("[0-9]+")) {
                throw new NumberFormatException();
            }

            int eventId = Integer.parseInt(id);

            if (eventId <= 0) {
                throw new NumberFormatException();
            }

            return eventId;
        } catch (NumberFormatException exception) {
            throw new InvalidEventIdException();
        }
    }
}
