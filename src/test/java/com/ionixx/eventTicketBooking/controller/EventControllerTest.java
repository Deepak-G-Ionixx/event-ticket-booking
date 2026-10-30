package com.ionixx.eventticketbooking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.ionixx.eventticketbooking.dto.EventResponse;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.service.BookingService;
import com.ionixx.eventticketbooking.service.EventService;

class EventControllerTest {

    @Mock
    private EventService eventService;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private EventController controller;

    private AutoCloseable mocks;
    private MockHttpServletRequest httpRequest;

    @BeforeEach
    void setUp() {
        // Initialize the Mockito mocks before every test.
        mocks = MockitoAnnotations.openMocks(this);

        // Prevent MDC values from leaking between tests.
        MDC.clear();

        // Create a mock HTTP request with the same URI as the events API.
        httpRequest = new MockHttpServletRequest("POST", "/api/events");

        // Make the mock request available to Spring's request-context utilities.
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(httpRequest));
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clear logging context so one test cannot affect another.
        MDC.clear();

        // Remove the request context after each test.
        RequestContextHolder.resetRequestAttributes();

        // Close Mockito resources.
        if (mocks != null) {
            mocks.close();
        }
    }

    private EventResponse eventResponse() {
        return new EventResponse(
                7,
                "ETB0007",
                "Java Conference",
                "Chennai Hall",
                EventCategory.TECH,
                LocalDate.now().plusDays(40),
                200,
                new BigDecimal("1499.00"));
    }

    @Test
    void createEventReturns201WithLocationAndTagsRequestWithLogId() {
        // Arrange: return a sample event when the service creates an event.
        EventResponse response = eventResponse();
        when(eventService.createEvent(any())).thenReturn(response);

        // Act: call the controller directly.
        ResponseEntity<EventResponse> result =
                controller.createEvent(null, httpRequest);

        // Assert: verify the HTTP response and event logging information.
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Check the Location header from the HTTP response, not the DTO.
        assertThat(result.getHeaders().getLocation())
                .hasToString("http://localhost/api/events/7");

        assertThat(result.getBody()).isEqualTo(response);

        // The event log ID should be attached to the request and logging context.
        assertThat(httpRequest.getAttribute("eventLogId"))
                .isEqualTo("ETB0007");
        assertThat(MDC.get("eventLogId"))
                .isEqualTo("ETB0007");
    }

    @Test
    void getEventByIdReturnsEventAndTagsRequest() {
        // Arrange: return the event for ID 7.
        EventResponse response = eventResponse();
        when(eventService.getEventById(7)).thenReturn(response);

        // Act: request the event by ID.
        ResponseEntity<EventResponse> result =
                controller.getEventById("7", httpRequest);

        // Assert: verify the response and logging information.
        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isEqualTo(response);
        assertThat(httpRequest.getAttribute("eventLogId"))
                .isEqualTo("ETB0007");
        assertThat(MDC.get("eventLogId"))
                .isEqualTo("ETB0007");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "0",
            "-1",
            "1.5",
            "",
            " 7",
            "99999999999"
    })
    void getEventByIdRejectsInvalidIds(String id) {
        // Invalid IDs should cause the controller to reject the request.
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> controller.getEventById(id, httpRequest)))
                .isNotNull();
    }

    @Test
    void createEventUsesReturnedEventIdForLocation() {
        // Arrange: the service returns an event whose ID is 7.
        when(eventService.createEvent(any())).thenReturn(eventResponse());

        // Act: create the event.
        ResponseEntity<EventResponse> result =
                controller.createEvent(null, httpRequest);

        // Assert: the Location header must use the ID returned by the service.
        assertThat(result.getHeaders().getLocation())
                .hasToString("http://localhost/api/events/7");
    }
}
