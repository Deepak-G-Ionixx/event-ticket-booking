
package com.ionixx.eventticketbooking.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.exception.InvalidBookingIdException;
import com.ionixx.eventticketbooking.service.BookingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    // Constructor injection: Spring supplies the BookingService.
    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    // POST /api/bookings
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {

        BookingResponse response =
                bookingService.createBooking(request);

        URI location = URI.create("/api/bookings/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    // GET /api/bookings/{id}
    @GetMapping("/{id}")
    public ResponseEntity<BookingResponse> getBookingById(
            @PathVariable("id") String id) {

        final int bookingId;

        try {
            bookingId = Integer.parseInt(id);
        } catch (NumberFormatException exception) {
            throw new InvalidBookingIdException();
        }

        if (bookingId <= 0) {
            throw new InvalidBookingIdException();
        }

        BookingResponse response =
                bookingService.getBookingById(bookingId);

        return ResponseEntity.ok(response);
    }
}
