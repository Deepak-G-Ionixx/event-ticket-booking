package com.ionixx.eventticketbooking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.exception.DuplicateEventException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.repository.EventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    @InjectMocks
    private EventService eventService;

    @Test
    void createEventReturnsResponseFromSavedEvent() {
        LocalDate date = LocalDate.now().plusDays(30);
        CreateEventRequest request = new CreateEventRequest(
            "Java Conference",
            "Chennai Trade Centre",
            EventCategory.TECH,
            date,
            500,
            new BigDecimal("1499.00")
        );
        Event saved = new Event(
            "Java Conference",
            "Chennai Trade Centre",
            EventCategory.TECH,
            date,
            500,
            new BigDecimal("1499.00")
        );

        when(eventRepository.existsByNameAndEventDate("Java Conference", date)).thenReturn(false);
        when(eventRepository.saveAndFlush(any(Event.class))).thenAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            ReflectionTestUtils.setField(event, "id", 1);
            return event;
        });

        EventResponse response = eventService.createEvent(request);

        assertEquals(Integer.valueOf(1), response.id());
        assertEquals("ETB0001", response.eventLogId());
        assertEquals("Java Conference", response.name());
        verify(eventRepository, org.mockito.Mockito.times(2)).saveAndFlush(any(Event.class));
        assertEquals(EventCategory.TECH, response.category());
        assertEquals(new BigDecimal("1499.00"), response.basePrice());
    }

    @Test
    void createEventRejectsDuplicateNameAndDate() {
        LocalDate date = LocalDate.now().plusDays(30);
        CreateEventRequest request = new CreateEventRequest(
            "Java Conference",
            "Chennai Trade Centre",
            EventCategory.TECH,
            date,
            500,
            new BigDecimal("1499.00")
        );
        when(eventRepository.existsByNameAndEventDate("Java Conference", date)).thenReturn(true);

        assertThrows(DuplicateEventException.class, () -> eventService.createEvent(request));
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void getEventByIdThrowsWhenEventDoesNotExist() {
        when(eventRepository.findById(404)).thenReturn(Optional.empty());

        assertThrows(EventNotFoundException.class, () -> eventService.getEventById(404));
    }
}
