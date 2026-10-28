package com.ionixx.eventticketbooking.service;

import java.time.LocalDate;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.exception.DuplicateEventException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.repository.EventRepository;

@Service
public class EventService {

    private static final String UNIQUE_CONSTRAINT =
            "uk_event_name_event_date";

    private static final Logger log =
            LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;

    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {

        // 1. Validate that the event date is in the future.
        if (request.eventDate() == null
                || !request.eventDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Event date must be in the future"
            );
        }

        // 2. Check whether an event with the same name and date exists.
        if (eventRepository.existsByNameAndEventDate(
                request.name(),
                request.eventDate())) {
            throw new DuplicateEventException();
        }

        // 3. Create the event entity from the request.
        Event event = new Event(
                request.name(),
                request.venue(),
                request.category(),
                request.eventDate(),
                request.totalSeats(),
                request.basePrice()
        );

        try {
            // 4. First save: PostgreSQL generates the numeric primary key.
            // The event_log_id column must temporarily allow NULL for this insert.
            Event savedEvent = eventRepository.saveAndFlush(event);

            // 5. Build the human-readable event log ID from the database ID.
            // %04d means at least four digits: 1 -> ETB0001, 13 -> ETB0013.
            String eventLogId = "ETB%04d".formatted(savedEvent.getId());
            savedEvent.setEventLogId(eventLogId);

            // 6. Second save updates event_log_id within this same transaction.
            savedEvent = eventRepository.saveAndFlush(savedEvent);

            // 7. Include the event ID in structured logs for this operation.
            String previousEventLogId = MDC.get("eventLogId");
            MDC.put("eventLogId", savedEvent.getEventLogId());
            try {
                log.info(
                        "Event created successfully: eventLogId={}, eventId={}",
                        savedEvent.getEventLogId(),
                        savedEvent.getId()
                );
            } finally {
                if (previousEventLogId == null) {
                    MDC.remove("eventLogId");
                } else {
                    MDC.put("eventLogId", previousEventLogId);
                }
            }

            // 8. Return the saved event as a response DTO.
            return toResponse(savedEvent);

        } catch (DataIntegrityViolationException exception) {

            // Handle duplicate event name and date.
            if (isDuplicateNameDateConstraint(exception)) {
                throw new DuplicateEventException();
            }

            // Preserve other database errors.
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public EventResponse getEventById(Integer id) {

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        return toResponse(event);
    }

    private EventResponse toResponse(Event event) {

        return new EventResponse(
                event.getId(),
                event.getEventLogId(),
                event.getName(),
                event.getVenue(),
                event.getCategory(),
                event.getEventDate(),
                event.getTotalSeats(),
                event.getBasePrice()
        );
    }

    private boolean isDuplicateNameDateConstraint(Throwable exception) {

        Throwable current = exception;

        while (current != null) {

            if (current instanceof ConstraintViolationException violation) {
                return UNIQUE_CONSTRAINT.equals(
                        violation.getConstraintName()
                );
            }

            current = current.getCause();
        }

        return false;
    }
}
