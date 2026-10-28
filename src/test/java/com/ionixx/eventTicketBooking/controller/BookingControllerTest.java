package com.ionixx.eventticketbooking.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.exception.InvalidBookingIdException;
import com.ionixx.eventticketbooking.service.BookingService;

class BookingControllerTest {

    private final BookingService bookingService = mock(BookingService.class);
    private final BookingController controller = new BookingController(bookingService);

    private static BookingResponse bookingResponse(int id) {
        return new BookingResponse(
                id, 1, "John Doe", "john@example.com",
                List.of(1, 2), 2,
                new BigDecimal("900.00"), new BigDecimal("1800.00"),
                "CONFIRMED", null, LocalDate.now());
    }

    @Test
    void createBookingReturns201WithLocationHeader() {
        CreateBookingRequest request = new CreateBookingRequest(
                1, "John Doe", "john@example.com", List.of(1, 2));
        BookingResponse response = bookingResponse(15);
        when(bookingService.createBooking(request)).thenReturn(response);

        ResponseEntity<BookingResponse> result = controller.createBooking(request);

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(result.getHeaders().getLocation()).hasToString("/api/bookings/15");
        assertThat(result.getBody()).isSameAs(response);
    }

    @Test
    void getBookingByIdReturnsBooking() {
        BookingResponse response = bookingResponse(8);
        when(bookingService.getBookingById(8)).thenReturn(response);

        ResponseEntity<BookingResponse> result = controller.getBookingById("8");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getBody()).isSameAs(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "", "1.5", "99999999999", "0", "-4"})
    void getBookingByIdRejectsInvalidIds(String id) {
        assertThatThrownBy(() -> controller.getBookingById(id))
                .isInstanceOf(InvalidBookingIdException.class)
                .hasMessage("Booking ID must be a positive integer");

        verify(bookingService, never()).getBookingById(org.mockito.ArgumentMatchers.any());
    }
}
