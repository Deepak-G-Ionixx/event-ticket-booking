package com.ionixx.eventticketbooking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.exception.InvalidEventIdException;
import com.ionixx.eventticketbooking.service.BookingService;
import com.ionixx.eventticketbooking.service.EventService;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EventControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private BookingService bookingService;

    private EventController controller;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        controller = new EventController(eventService, bookingService);
        httpRequest = new MockHttpServletRequest("GET", "/api/events");
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    private static EventResponse eventResponse() {
        return new EventResponse(
                7, "ETB0007", "Java Conference", "Chennai Hall",
                EventCategory.TECH, LocalDate.now().plusDays(40),
                200, new BigDecimal("1499.00"));
    }

    // ------------------------------------------------------------------
    // POST /api/events
    // ------------------------------------------------------------------

    @Test
    void createEventReturns201WithLocationAndTagsRequestWithLogId() {
        CreateEventRequest request = new CreateEventRequest(
                "Java Conference", "Chennai Hall", EventCategory.TECH,
                LocalDate.now().plusDays(40), 200, new BigDecimal("1499.00"));
        EventResponse response = eventResponse();
        when(eventService.createEvent(request)).thenReturn(response);

        ResponseEntity<EventResponse> result = controller.createEvent(request, httpRequest);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).hasToString("/api/events/7");
        assertThat(result.getBody()).isSameAs(response);
        assertThat(httpRequest.getAttribute("eventLogId")).isEqualTo("ETB0007");
        assertThat(MDC.get("eventLogId")).isEqualTo("ETB0007");
    }

    // ------------------------------------------------------------------
    // GET /api/events/{id}
    // ------------------------------------------------------------------

    @Test
    void getEventByIdReturnsEventAndTagsRequest() {
        EventResponse response = eventResponse();
        when(eventService.getEventById(7)).thenReturn(response);

        ResponseEntity<EventResponse> result = controller.getEventById("7", httpRequest);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
        assertThat(httpRequest.getAttribute("eventLogId")).isEqualTo("ETB0007");
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "1.5", "", " 7", "99999999999"})
    void getEventByIdRejectsInvalidIds(String id) {
        assertThatThrownBy(() -> controller.getEventById(id, httpRequest))
                .isInstanceOf(InvalidEventIdException.class)
                .hasMessage("Event ID must be a positive integer");
    }

    // ------------------------------------------------------------------
    // GET /api/events/{id}/bookings
    // ------------------------------------------------------------------

    @Test
    void getEventBookingsWithoutStatusPassesNullFilter() {
        List<BookingResponse> bookings = List.of();
        when(bookingService.getEventBookings(3, null)).thenReturn(bookings);

        ResponseEntity<List<BookingResponse>> result = controller.getEventBookings("3", null);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(bookings);
    }

    @Test
    void getEventBookingsParsesConfirmedStatus() {
        controller.getEventBookings("3", "CONFIRMED");

        verify(bookingService).getEventBookings(3, BookingStatus.CONFIRMED);
    }

    @Test
    void getEventBookingsParsesRejectedStatus() {
        controller.getEventBookings("3", "REJECTED");

        verify(bookingService).getEventBookings(3, BookingStatus.REJECTED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING", "confirmed", ""})
    void getEventBookingsRejectsUnknownStatus(String status) {
        assertThatThrownBy(() -> controller.getEventBookings("3", status))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status must be CONFIRMED or REJECTED");
    }

    @Test
    void getEventBookingsRejectsInvalidEventId() {
        assertThatThrownBy(() -> controller.getEventBookings("x", null))
                .isInstanceOf(InvalidEventIdException.class);
    }

    // ------------------------------------------------------------------
    // GET /api/events/{id}/seats and /summary
    // ------------------------------------------------------------------

    @Test
    void getEventSeatsReturnsSeatMap() {
        EventSeatMapResponse seatMap =
                new EventSeatMapResponse(4, 10, List.of(1), List.of(2, 3));
        when(bookingService.getEventSeatMap(4)).thenReturn(seatMap);

        ResponseEntity<EventSeatMapResponse> result = controller.getEventSeats("4");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(seatMap);
    }

    @Test
    void getEventSeatsRejectsInvalidEventId() {
        assertThatThrownBy(() -> controller.getEventSeats("0"))
                .isInstanceOf(InvalidEventIdException.class);
    }

    @Test
    void getEventSummaryReturnsSummary() {
        EventSummaryResponse summary = new EventSummaryResponse(
                4, "Show", 10, 2, 8, 1, 0,
                new BigDecimal("100.00"), new BigDecimal("20.0"), "LOW");
        when(bookingService.getEventSummary(4)).thenReturn(summary);

        ResponseEntity<EventSummaryResponse> result = controller.getEventSummary("4");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(summary);
    }

    @Test
    void getEventSummaryRejectsInvalidEventId() {
        assertThatThrownBy(() -> controller.getEventSummary("abc"))
                .isInstanceOf(InvalidEventIdException.class);
    }
}