package com.ionixx.eventticketbooking.service;

import java.time.LocalDate;
import java.util.Map;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ionixx.eventticketbooking.security.AuthenticatedUserService;
import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.DuplicateEventException;
import com.ionixx.eventticketbooking.exception.EventHasBookingsException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.repository.EventRepository;
import com.ionixx.eventticketbooking.security.AccessRules;
import com.ionixx.eventticketbooking.security.CurrentUser;
import com.ionixx.eventticketbooking.security.UserPrincipal;

@Service
public class EventService {

    private static final String UNIQUE_CONSTRAINT =
            "uk_event_name_event_date";

    /**
     * Properties a client may sort "my events" by (API name -> entity property).
     */
    static final Map<String, String> EVENT_SORT_PROPERTIES = Map.of(
            "id", "id",
            "name", "name",
            "venue", "venue",
            "category", "category",
            "eventDate", "eventDate",
            "event_date", "eventDate",
            "totalSeats", "totalSeats",
            "total_seats", "totalSeats",
            "basePrice", "basePrice",
            "base_price", "basePrice");

    // Soonest events first. ID provides a consistent tie-breaker.
    private static final Sort DEFAULT_EVENT_SORT = Sort.by(
            Sort.Order.asc("eventDate"),
            Sort.Order.asc("id"));

    private static final Sort.Order EVENT_TIE_BREAKER = Sort.Order.asc("id");

    private static final Logger log =
            LoggerFactory.getLogger(EventService.class);

    private final EventRepository eventRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public EventService(
            EventRepository eventRepository,
            AuthenticatedUserService authenticatedUserService) {
        this.eventRepository = eventRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    @Transactional
    public EventResponse createEvent(CreateEventRequest request) {

        // The logged-in organiser becomes the owner of the new event.
        // The account is loaded from the database using the id in the
        // verified JWT. The request body has no owner field at all, and any
        // unknown JSON property such as "organiserId" is ignored.
        User organiser = authenticatedUserService
                .requireCurrentUser(Role.ORGANISER);

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
                request.basePrice(),
                organiser.getId()
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

        UserPrincipal user = CurrentUser.require();

        Event event = eventRepository.findById(id)
                .orElseThrow(() -> new EventNotFoundException(id));

        // Customers can browse any event; an organiser can open only their own.
        AccessRules.requireCanViewEvent(user, event);

        return toResponse(event);
    }

    /**
     * Replaces the details of an event.
     * Only the organiser who owns the event may do this.
     *
     * The event is loaded with an owner-scoped query that also locks the row,
     * so a booking cannot slip in between the "highest booked seat" check and
     * the update.
     */
    @Transactional
    public EventResponse updateEvent(Integer id, CreateEventRequest request) {

        UserPrincipal user = CurrentUser.require();

        // Ownership check first: another organiser's event is never modified.
        Event event = OwnedEvents.require(
                user,
                id,
                organiserId -> eventRepository.findOwnedByIdForUpdate(
                        id, organiserId),
                eventRepository);

        if (request.eventDate() == null
                || !request.eventDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Event date must be in the future"
            );
        }

        if (eventRepository.existsByNameAndEventDateAndIdNot(
                request.name(),
                request.eventDate(),
                id)) {
            throw new DuplicateEventException();
        }

        // Do not shrink the hall below a seat that is already reserved.
        Integer highestReservedSeat = eventRepository.findHighestReservedSeat(id);

        if (highestReservedSeat != null
                && request.totalSeats() < highestReservedSeat) {
            throw new IllegalArgumentException(
                    "Total seats cannot be lower than the highest booked seat ("
                            + highestReservedSeat + ")"
            );
        }

        event.updateDetails(
                request.name(),
                request.venue(),
                request.category(),
                request.eventDate(),
                request.totalSeats(),
                request.basePrice()
        );

        try {
            return toResponse(eventRepository.saveAndFlush(event));
        } catch (DataIntegrityViolationException exception) {
            if (isDuplicateNameDateConstraint(exception)) {
                throw new DuplicateEventException();
            }
            throw exception;
        }
    }

    /**
     * Deletes an event that has no bookings.
     * Only the organiser who owns the event may do this.
     *
     * The event row is locked first, so no booking can be created between
     * the "has bookings" check and the delete.
     */
    @Transactional
    public void deleteEvent(Integer id) {

        UserPrincipal user = CurrentUser.require();

        Event event = OwnedEvents.require(
                user,
                id,
                organiserId -> eventRepository.findOwnedByIdForUpdate(
                        id, organiserId),
                eventRepository);

        if (eventRepository.hasBookings(id)) {
            throw new EventHasBookingsException(id);
        }

        eventRepository.delete(event);
        eventRepository.flush();
    }

    /**
     * One page of the events owned by the logged-in organiser.
     *
     * @param sort optional, for example "name,asc" or "eventDate,desc"
     */
    @Transactional(readOnly = true)
    public Page<EventResponse> getMyEvents(int page, int size, String sort) {

        UserPrincipal user = CurrentUser.require();

        Pageable pageable = PagingSupport.pageRequest(
                page,
                size,
                sort,
                EVENT_SORT_PROPERTIES,
                DEFAULT_EVENT_SORT,
                EVENT_TIE_BREAKER);

        // The organiser id comes from the JWT, never from the request.
        return eventRepository
                .findAllByOrganiserId(user.id(), pageable)
                .map(this::toResponse);
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
