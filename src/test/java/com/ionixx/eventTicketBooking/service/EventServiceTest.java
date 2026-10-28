package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.exception.DuplicateEventException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.repository.EventRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static CreateEventRequest requestFor(LocalDate date) {
        return new CreateEventRequest(
                "Java Conference",
                "Chennai Trade Centre",
                EventCategory.TECH,
                date,
                500,
                new BigDecimal("1499.00"));
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

    // ------------------------------------------------------------------
    // original tests
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
    void createEventRejectsDuplicateNameAndDate() {
        LocalDate date = LocalDate.now().plusDays(30);
        CreateEventRequest request = requestFor(date);
        when(eventRepository.existsByNameAndEventDate("Java Conference", date)).thenReturn(true);

        assertThrows(DuplicateEventException.class, () -> eventService.createEvent(request));
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void getEventByIdThrowsWhenEventDoesNotExist() {
        when(eventRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> eventService.getEventById(404));
    }

    // ------------------------------------------------------------------
    // date validation
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // MDC handling
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // database constraint handling
    // ------------------------------------------------------------------

    @Test
    void createEventTranslatesDuplicateConstraintViolation() {
        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("uk_event_name_event_date");
        when(eventRepository.saveAndFlush(any(Event.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate", violation));

        assertThrows(DuplicateEventException.class,
                () -> eventService.createEvent(requestFor(LocalDate.now().plusDays(10))));
    }

    @Test
    void createEventRethrowsOtherConstraintViolations() {
        ConstraintViolationException violation = mock(ConstraintViolationException.class);
        when(violation.getConstraintName()).thenReturn("uk_event_event_log_id");
        DataIntegrityViolationException original =
                new DataIntegrityViolationException("other", violation);
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
        Event event = new Event(
                "Cricket Final", "Chepauk", EventCategory.SPORTS,
                LocalDate.now().plusDays(5), 100, new BigDecimal("250.00"));
        ReflectionTestUtils.setField(event, "id", 9);
        event.setEventLogId("ETB0009");
        when(eventRepository.findById(9)).thenReturn(Optional.of(event));

        EventResponse response = eventService.getEventById(9);

        assertThat(response.id()).isEqualTo(9);
        assertThat(response.eventLogId()).isEqualTo("ETB0009");
        assertThat(response.name()).isEqualTo("Cricket Final");
        assertThat(response.venue()).isEqualTo("Chepauk");
        assertThat(response.category()).isEqualTo(EventCategory.SPORTS);
        assertThat(response.totalSeats()).isEqualTo(100);
        assertThat(response.basePrice()).isEqualByComparingTo("250.00");
    }
}
