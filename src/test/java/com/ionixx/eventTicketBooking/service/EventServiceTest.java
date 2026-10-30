package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ionixx.eventticketbooking.security.AuthenticatedUserService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.DuplicateEventException;
import com.ionixx.eventticketbooking.exception.EventHasBookingsException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;
import com.ionixx.eventticketbooking.repository.EventRepository;
import com.ionixx.eventticketbooking.security.SecurityTestSupport;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    private static final int ORGANISER_ID = 7;
    private static final int OTHER_ORGANISER_ID = 8;
    private static final int EVENT_ID = 9;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    private EventService eventService;

    // The service reads the logged-in user from the security context and,
    // for writes, loads the organiser's account through AuthenticatedUserService.
    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository, authenticatedUserService);

        SecurityTestSupport.actAs(Role.ORGANISER, ORGANISER_ID);

        User organiser = new User(
                "Organiser", "organiser@example.com", "hash", Role.ORGANISER);
        ReflectionTestUtils.setField(organiser, "id", ORGANISER_ID);
        when(authenticatedUserService.requireCurrentUser(Role.ORGANISER))
                .thenReturn(organiser);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
        SecurityTestSupport.clear();
    }

    private static CreateEventRequest requestFor(LocalDate date) {
        return requestFor(date, 500);
    }

    private static CreateEventRequest requestFor(LocalDate date, int totalSeats) {
        return new CreateEventRequest(
                "Java Conference",
                "Chennai Trade Centre",
                EventCategory.TECH,
                date,
                totalSeats,
                new BigDecimal("1499.00"));
    }

    private static Event existingEvent() {
        Event event = new Event(
                "Cricket Final", "Chepauk", EventCategory.SPORTS,
                LocalDate.now().plusDays(5), 100, new BigDecimal("250.00"),
                ORGANISER_ID);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        event.setEventLogId("ETB0009");
        return event;
    }

    private void saveAssignsId(int id) {
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            if (event.getId() == null) {
                ReflectionTestUtils.setField(event, "id", id);
            }
            return event;
        });
    }

    private void givenOwnedEventLockedForUpdate(Event event) {
        when(eventRepository.findOwnedByIdForUpdate(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.of(event));
    }

    private static DataIntegrityViolationException constraintViolation(String name) {
        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn(name);
        return new DataIntegrityViolationException(name, violation);
    }

    // ------------------------------------------------------------------
    // createEvent
    // ------------------------------------------------------------------

    @Test
    void createEventReturnsResponseFromSavedEvent() {
        LocalDate date = LocalDate.now().plusDays(30);
        CreateEventRequest request = requestFor(date);

        when(eventRepository.existsByNameAndEventDate("Java Conference", date)).thenReturn(false);
        saveAssignsId(1);

        EventResponse response = eventService.createEvent(request);

        assertEquals(Integer.valueOf(1), response.id());
        assertEquals("ETB0001", response.eventLogId());
        assertEquals("Java Conference", response.name());
        verify(eventRepository, times(2)).saveAndFlush(any(Event.class));
        assertEquals(EventCategory.TECH, response.category());
        assertEquals(new BigDecimal("1499.00"), response.basePrice());
    }

    @Test
    void createEventAssignsTheAuthenticatedOrganiserAsOwner() {
        saveAssignsId(3);

        eventService.createEvent(requestFor(LocalDate.now().plusDays(30)));

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues().get(0).getOrganiserId()).isEqualTo(ORGANISER_ID);
        verify(authenticatedUserService).requireCurrentUser(Role.ORGANISER);
    }

    @Test
    void createEventIsDeniedWhenTheCallerIsNotAnOrganiser() {
        when(authenticatedUserService.requireCurrentUser(Role.ORGANISER))
                .thenThrow(new ResourceAccessDeniedException());

        assertThrows(ResourceAccessDeniedException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().plusDays(30))));
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void createEventRejectsDuplicateNameAndDate() {
        LocalDate date = LocalDate.now().plusDays(30);
        when(eventRepository.existsByNameAndEventDate("Java Conference", date)).thenReturn(true);

        assertThrows(DuplicateEventException.class,
                () -> eventService.createEvent(requestFor(date)));
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void createEventRejectsNullDate() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(requestFor(null)));

        assertEquals("Event date must be in the future", ex.getMessage());
        verify(eventRepository, never()).existsByNameAndEventDate(any(), any());
    }

    @Test
    void createEventRejectsPastDate() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().minusDays(1))));
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void createEventRejectsTodayBecauseDateMustBeInTheFuture() {
        assertThrows(IllegalArgumentException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now())));
    }

    @Test
    void createEventRemovesEventLogIdFromMdcWhenNoPreviousValueExisted() {
        saveAssignsId(13);

        EventResponse response =
                eventService.createEvent(requestFor(LocalDate.now().plusDays(10)));

        assertThat(response.eventLogId()).isEqualTo("ETB0013");
        assertThat(MDC.get("eventLogId")).isNull();
    }

    @Test
    void createEventRestoresPreviousEventLogIdInMdc() {
        MDC.put("eventLogId", "PREVIOUS");
        saveAssignsId(2);

        eventService.createEvent(requestFor(LocalDate.now().plusDays(10)));

        assertThat(MDC.get("eventLogId")).isEqualTo("PREVIOUS");
    }

    @Test
    void createEventTranslatesDuplicateConstraintViolation() {
        // Build the exception first: stubbing inside thenThrow(...) is not allowed.
        DataIntegrityViolationException duplicate =
                constraintViolation("uk_event_name_event_date");
        when(eventRepository.saveAndFlush(any(Event.class))).thenThrow(duplicate);

        assertThrows(DuplicateEventException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().plusDays(10))));
    }

    @Test
    void createEventRethrowsOtherConstraintViolations() {
        DataIntegrityViolationException original = constraintViolation("uk_event_event_log_id");
        when(eventRepository.saveAndFlush(any(Event.class))).thenThrow(original);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().plusDays(10))));

        assertSame(original, thrown);
    }

    @Test
    void createEventRethrowsIntegrityErrorWithoutConstraintCause() {
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("no cause");
        when(eventRepository.saveAndFlush(any(Event.class))).thenThrow(original);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().plusDays(10))));

        assertSame(original, thrown);
    }

    @Test
    void createEventFindsConstraintViolationNestedDeeperInCauseChain() {
        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("uk_event_name_event_date");
        RuntimeException wrapper = new RuntimeException("wrapper", violation);
        when(eventRepository.saveAndFlush(any(Event.class)))
                .thenThrow(new DataIntegrityViolationException("dup", wrapper));

        assertThrows(DuplicateEventException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().plusDays(10))));
    }

    // ------------------------------------------------------------------
    // getEventById
    // ------------------------------------------------------------------

    @Test
    void getEventByIdReturnsMappedEvent() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent()));

        EventResponse response = eventService.getEventById(EVENT_ID);

        assertThat(response.id()).isEqualTo(EVENT_ID);
        assertThat(response.eventLogId()).isEqualTo("ETB0009");
        assertThat(response.name()).isEqualTo("Cricket Final");
        assertThat(response.venue()).isEqualTo("Chepauk");
        assertThat(response.category()).isEqualTo(EventCategory.SPORTS);
        assertThat(response.totalSeats()).isEqualTo(100);
        assertThat(response.basePrice()).isEqualByComparingTo("250.00");
    }

    @Test
    void getEventByIdThrowsWhenEventDoesNotExist() {
        when(eventRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> eventService.getEventById(404));
    }

    // ------------------------------------------------------------------
    // updateEvent
    // ------------------------------------------------------------------

    @Test
    void ownerUpdatesTheEventThroughTheLockedOwnerScopedQuery() {
        Event event = existingEvent();
        givenOwnedEventLockedForUpdate(event);
        when(eventRepository.findHighestReservedSeat(EVENT_ID)).thenReturn(12);
        when(eventRepository.saveAndFlush(event)).thenReturn(event);

        LocalDate newDate = LocalDate.now().plusDays(40);
        EventResponse response = eventService.updateEvent(EVENT_ID, requestFor(newDate, 300));

        assertThat(response.name()).isEqualTo("Java Conference");
        assertThat(response.totalSeats()).isEqualTo(300);
        assertThat(response.eventDate()).isEqualTo(newDate);
        // The owner never changes.
        assertThat(event.getOrganiserId()).isEqualTo(ORGANISER_ID);
        verify(eventRepository).findOwnedByIdForUpdate(EVENT_ID, ORGANISER_ID);
    }

    @Test
    void updateWorksWhenNoSeatIsReserved() {
        Event event = existingEvent();
        givenOwnedEventLockedForUpdate(event);
        when(eventRepository.findHighestReservedSeat(EVENT_ID)).thenReturn(null);
        when(eventRepository.saveAndFlush(event)).thenReturn(event);

        assertThat(eventService.updateEvent(
                EVENT_ID, requestFor(LocalDate.now().plusDays(40), 20)).totalSeats())
                .isEqualTo(20);
    }

    @Test
    void updateOfAnotherOrganisersEventIsDenied() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER_ID);
        when(eventRepository.findOwnedByIdForUpdate(EVENT_ID, OTHER_ORGANISER_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThrows(ResourceAccessDeniedException.class,
                () -> eventService.updateEvent(EVENT_ID, requestFor(LocalDate.now().plusDays(40))));
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void updateOfMissingEventIsNotFound() {
        when(eventRepository.findOwnedByIdForUpdate(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(false);

        assertThrows(EventNotFoundException.class,
                () -> eventService.updateEvent(EVENT_ID, requestFor(LocalDate.now().plusDays(40))));
    }

    @Test
    void customerCannotUpdateAnEvent() {
        SecurityTestSupport.actAs(Role.CUSTOMER, 30);
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThrows(ResourceAccessDeniedException.class,
                () -> eventService.updateEvent(EVENT_ID, requestFor(LocalDate.now().plusDays(40))));
        verify(eventRepository, never()).findOwnedByIdForUpdate(any(), any());
    }

    @Test
    void updateRejectsNullAndPastDates() {
        givenOwnedEventLockedForUpdate(existingEvent());

        assertThatThrownBy(() -> eventService.updateEvent(EVENT_ID, requestFor(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Event date must be in the future");
        assertThatThrownBy(() -> eventService.updateEvent(
                EVENT_ID, requestFor(LocalDate.now().minusDays(1))))
                .isInstanceOf(IllegalArgumentException.class);

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void updateRejectsANameAndDateUsedByAnotherEvent() {
        givenOwnedEventLockedForUpdate(existingEvent());
        LocalDate date = LocalDate.now().plusDays(40);
        when(eventRepository.existsByNameAndEventDateAndIdNot("Java Conference", date, EVENT_ID))
                .thenReturn(true);

        assertThrows(DuplicateEventException.class,
                () -> eventService.updateEvent(EVENT_ID, requestFor(date)));
    }

    @Test
    void updateCannotShrinkBelowTheHighestReservedSeat() {
        givenOwnedEventLockedForUpdate(existingEvent());
        when(eventRepository.findHighestReservedSeat(EVENT_ID)).thenReturn(15);

        assertThatThrownBy(() -> eventService.updateEvent(
                EVENT_ID, requestFor(LocalDate.now().plusDays(40), 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Total seats cannot be lower than the highest booked seat (15)");

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void updateTranslatesDuplicateConstraintRaisedOnSave() {
        Event event = existingEvent();
        givenOwnedEventLockedForUpdate(event);
        when(eventRepository.findHighestReservedSeat(EVENT_ID)).thenReturn(0);
        DataIntegrityViolationException duplicate =
                constraintViolation("uk_event_name_event_date");
        when(eventRepository.saveAndFlush(event)).thenThrow(duplicate);

        assertThrows(DuplicateEventException.class,
                () -> eventService.updateEvent(EVENT_ID, requestFor(LocalDate.now().plusDays(40))));
    }

    @Test
    void updateRethrowsOtherIntegrityErrors() {
        Event event = existingEvent();
        givenOwnedEventLockedForUpdate(event);
        when(eventRepository.findHighestReservedSeat(EVENT_ID)).thenReturn(0);
        DataIntegrityViolationException original = constraintViolation("ck_event_total_seats");
        when(eventRepository.saveAndFlush(event)).thenThrow(original);

        DataIntegrityViolationException thrown = assertThrows(
                DataIntegrityViolationException.class,
                () -> eventService.updateEvent(EVENT_ID, requestFor(LocalDate.now().plusDays(40))));

        assertSame(original, thrown);
    }

    // ------------------------------------------------------------------
    // deleteEvent
    // ------------------------------------------------------------------

    @Test
    void ownerDeletesAnEventWithoutBookings() {
        Event event = existingEvent();
        givenOwnedEventLockedForUpdate(event);
        when(eventRepository.hasBookings(EVENT_ID)).thenReturn(false);

        eventService.deleteEvent(EVENT_ID);

        verify(eventRepository).delete(event);
        verify(eventRepository).flush();
    }

    @Test
    void eventWithBookingsCannotBeDeleted() {
        givenOwnedEventLockedForUpdate(existingEvent());
        when(eventRepository.hasBookings(EVENT_ID)).thenReturn(true);

        assertThrows(EventHasBookingsException.class, () -> eventService.deleteEvent(EVENT_ID));
        verify(eventRepository, never()).delete(any(Event.class));
    }

    @Test
    void deleteOfAnotherOrganisersEventIsDenied() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER_ID);
        when(eventRepository.findOwnedByIdForUpdate(EVENT_ID, OTHER_ORGANISER_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThrows(ResourceAccessDeniedException.class, () -> eventService.deleteEvent(EVENT_ID));
        verify(eventRepository, never()).delete(any(Event.class));
    }

    // ------------------------------------------------------------------
    // getMyEvents
    // ------------------------------------------------------------------

    @Test
    void myEventsUsesTheAuthenticatedOrganiserAndDefaultSort() {
        when(eventRepository.findAllByOrganiserId(eq(ORGANISER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(existingEvent())));

        Page<EventResponse> page = eventService.getMyEvents(0, 10, null);

        assertThat(page.getContent()).extracting(EventResponse::id).containsExactly(EVENT_ID);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepository).findAllByOrganiserId(eq(ORGANISER_ID), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("eventDate"), Sort.Order.asc("id")));
    }

    @Test
    void myEventsAcceptsAWhitelistedSort() {
        when(eventRepository.findAllByOrganiserId(eq(ORGANISER_ID), any(Pageable.class)))
                .thenReturn(Page.empty());

        eventService.getMyEvents(1, 5, "name,desc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(eventRepository).findAllByOrganiserId(eq(ORGANISER_ID), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("name"), Sort.Order.asc("id")));
    }

    @Test
    void myEventsRejectsInvalidPagingAndSort() {
        assertThatThrownBy(() -> eventService.getMyEvents(-1, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page number must be zero or greater");
        assertThatThrownBy(() -> eventService.getMyEvents(0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page size must be between 1 and 100");
        assertThatThrownBy(() -> eventService.getMyEvents(0, 10, "organiserId"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(eventRepository, never()).findAllByOrganiserId(any(), any(Pageable.class));
    }
}
