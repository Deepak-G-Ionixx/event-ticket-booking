
package com.ionixx.eventticketbooking.controller;

import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.exception.InvalidEventIdException;
import com.ionixx.eventticketbooking.service.EventService;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Logger log =
            LoggerFactory.getLogger(EventController.class);

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
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
                response.id()
        );

        return ResponseEntity.created(location)
                .body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<EventResponse> getEventById(
            @PathVariable String id,
            HttpServletRequest httpRequest) {

        // Reject non-numeric IDs before parsing.
        if (!id.matches("[0-9]+")) {
            throw new InvalidEventIdException();
        }

        final int eventId;

        try {
            eventId = Integer.parseInt(id);
        } catch (NumberFormatException exception) {
            // Handles numeric strings too large for an Integer.
            throw new InvalidEventIdException();
        }

        // IDs must be positive integers.
        if (eventId <= 0) {
            throw new InvalidEventIdException();
        }

        EventResponse response = eventService.getEventById(eventId);
        attachEventLogIdToRequest(httpRequest, response.eventLogId());

        log.info(
                "Event retrieved successfully: eventLogId={}, eventId={}",
                response.eventLogId(),
                response.id()
        );

        return ResponseEntity.ok(response);
    }

    private void attachEventLogIdToRequest(
            HttpServletRequest request,
            String eventLogId) {
        request.setAttribute("eventLogId", eventLogId);
        MDC.put("eventLogId", eventLogId);
    }

}
