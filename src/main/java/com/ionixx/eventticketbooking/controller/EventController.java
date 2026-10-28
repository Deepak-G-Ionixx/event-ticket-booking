package com.ionixx.eventticketbooking.controller;

import java.net.URI;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.exception.InvalidEventIdException;
import com.ionixx.eventticketbooking.service.BookingService;
import com.ionixx.eventticketbooking.service.EventService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Logger log =
            LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;
    private final BookingService bookingService;

    public EventController(
            EventService eventService,
            BookingService bookingService) {
        this.eventService = eventService;
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<EventResponse> createEvent(
            @Valid @RequestBody CreateEventRequest request,
            HttpServletRequest httpRequest) {

        EventResponse response = eventService.createEvent(request);
        attachEventLogIdToRequest(httpRequest, response.eventLogId());

        URI location = URI.create("/api/events/" + response.id());

        log.info(
                "Event creation response prepared: eventLogId={}, eventId={}",
                response.eventLogId(),
                response.id());

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        Integer eventId = parseEventId(id);

        EventResponse response = eventService.getEventById(eventId);
        attachEventLogIdToRequest(httpRequest, response.eventLogId());

        log.info(
                "Event retrieved successfully: eventLogId={}, eventId={}",
                response.eventLogId(),
                response.id());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/bookings")
    public ResponseEntity<List<BookingResponse>> getEventBookings(
            @PathVariable String id,
            @RequestParam(required = false) String status) {

        Integer eventId = parseEventId(id);

        BookingStatus bookingStatus = null;

        if (status != null) {
            try {
                bookingStatus = BookingStatus.valueOf(status);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "status must be CONFIRMED or REJECTED");
            }
        }

        return ResponseEntity.ok(
                bookingService.getEventBookings(eventId, bookingStatus));
    }

    @GetMapping("/{id}/seats")
    public ResponseEntity<EventSeatMapResponse> getEventSeats(
            @PathVariable String id) {

        Integer eventId = parseEventId(id);

        return ResponseEntity.ok(
                bookingService.getEventSeatMap(eventId));
    }

    @GetMapping("/{id}/summary")
    public ResponseEntity<EventSummaryResponse> getEventSummary(
            @PathVariable String id) {

        Integer eventId = parseEventId(id);

        return ResponseEntity.ok(
                bookingService.getEventSummary(eventId));
    }

    private Integer parseEventId(String id) {

        if (id == null || !id.matches("[0-9]+")) {
            throw new InvalidEventIdException();
        }

        try {
            int eventId = Integer.parseInt(id);

            if (eventId <= 0) {
                throw new InvalidEventIdException();
            }

            return eventId;

        } catch (NumberFormatException exception) {
            throw new InvalidEventIdException();
        }
    }

    private void attachEventLogIdToRequest(
            HttpServletRequest request,
            String eventLogId) {

        request.setAttribute("eventLogId", eventLogId);
        MDC.put("eventLogId", eventLogId);
    }
}
