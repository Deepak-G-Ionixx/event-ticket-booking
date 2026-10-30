package com.ionixx.eventticketbooking.controller;

import java.net.URI;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.entity.BookingStatus;
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
    // The booking always belongs to the logged-in customer. The service takes
    // the customer id and email from the authenticated account, not the body.
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request) {

        BookingResponse response =
                bookingService.createBooking(request);

        URI location = URI.create("/api/bookings/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    // GET /api/bookings/mine?page=0&size=10&status=CONFIRMED&sort=bookedOn,desc
    // Only the logged-in customer's own bookings. The literal path "mine"
    // is matched before the "/{id}" pattern below.
    // Several sort parameters (sort=seats,desc&sort=id,asc) arrive here as one
    // comma-separated String and are validated against a whitelist.
    @GetMapping("/mine")
    public ResponseEntity<Page<BookingResponse>> getMyBookings(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        BookingStatus bookingStatus = null;

        if (status != null) {
            if ("CONFIRMED".equals(status)) {
                bookingStatus = BookingStatus.CONFIRMED;
            } else if ("REJECTED".equals(status)) {
                bookingStatus = BookingStatus.REJECTED;
            } else {
                throw new IllegalArgumentException(
                        "status must be CONFIRMED or REJECTED");
            }
        }

        return ResponseEntity.ok(
                bookingService.getMyBookings(bookingStatus, page, size, sort));
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
