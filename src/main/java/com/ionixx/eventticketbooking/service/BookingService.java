
package com.ionixx.eventticketbooking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.exception.BookingNotFoundException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.exception.InvalidBookingRequestException;
import com.ionixx.eventticketbooking.exception.SeatOutOfRangeException;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.EventRepository;

@Service
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 6;
    private static final int MAX_SEATS_PER_CUSTOMER_PER_EVENT = 6;

    private static final BigDecimal EARLY_BIRD_MULTIPLIER =
            new BigDecimal("0.90");

    private static final BigDecimal GROUP_MULTIPLIER =
            new BigDecimal("0.95");

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;

    public BookingService(
            BookingRepository bookingRepository,
            EventRepository eventRepository) {
        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {

        validateSeatNumbers(request.seatNumbers());

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() ->
                        new EventNotFoundException(request.eventId()));

        // Every requested seat must exist for the event.
        for (Integer seatNumber : request.seatNumbers()) {
            if (seatNumber > event.getTotalSeats()) {
                throw new SeatOutOfRangeException(seatNumber);
            }
        }

        List<Integer> sortedSeats = request.seatNumbers().stream()
                .sorted()
                .toList();

        LocalDate today = LocalDate.now();

        String rejectionReason = determineRejectionReason(
                event,
                request.customerEmail(),
                sortedSeats,
                today);

        Booking booking = new Booking(
                event,
                request.customerName(),
                request.customerEmail(),
                sortedSeats.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")),
                sortedSeats.size(),
                rejectionReason == null
                        ? BookingStatus.CONFIRMED
                        : BookingStatus.REJECTED,
                rejectionReason,
                today);

        if (rejectionReason == null) {
            BigDecimal pricePerSeat = calculatePricePerSeat(
                    event,
                    sortedSeats.size(),
                    today);

            BigDecimal totalAmount = pricePerSeat
                    .multiply(BigDecimal.valueOf(sortedSeats.size()))
                    .setScale(2, RoundingMode.HALF_UP);

            booking.confirm(pricePerSeat, totalAmount);
        } else {
            booking.reject(rejectionReason);
        }

        Booking savedBooking = bookingRepository.save(booking);

        return toResponse(savedBooking);
    }

    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Integer id) {

        Booking booking = bookingRepository
                .findBookingWithEventById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        return toResponse(booking);
    }

    private void validateSeatNumbers(List<Integer> seatNumbers) {

        if (seatNumbers == null || seatNumbers.isEmpty()) {
            throw new InvalidBookingRequestException(
                    "seat_numbers",
                    "At least one seat number is required");
        }

        if (seatNumbers.size() > MAX_SEATS_PER_BOOKING) {
            throw new InvalidBookingRequestException(
                    "seat_numbers",
                    "A booking can contain at most 6 seats");
        }

        if (seatNumbers.stream()
                .anyMatch(seat -> seat == null || seat <= 0)) {
            throw new InvalidBookingRequestException(
                    "seat_numbers",
                    "Seat numbers must be positive integers");
        }

        Set<Integer> uniqueSeats = new TreeSet<>(seatNumbers);

        if (uniqueSeats.size() != seatNumbers.size()) {
            throw new InvalidBookingRequestException(
                    "seat_numbers",
                    "Duplicate seat numbers are not allowed");
        }
    }

    private String determineRejectionReason(
            Event event,
            String customerEmail,
            List<Integer> requestedSeats,
            LocalDate today) {

        // Rule 1: Reject bookings for events that have already ended.
        if (event.getEventDate().isBefore(today)) {
            return "Event is already over";
        }

        // Rule 2: Reject if any requested seat is already confirmed.
        List<Booking> confirmedBookings =
                bookingRepository.findAllByEvent_IdAndStatus(
                        event.getId(),
                        BookingStatus.CONFIRMED);

        Set<Integer> takenSeats = new TreeSet<>();

        for (Booking existingBooking : confirmedBookings) {
            takenSeats.addAll(
                    parseSeatNumbers(existingBooking.getSeatNumbers()));
        }

        List<Integer> conflicts = requestedSeats.stream()
                .filter(takenSeats::contains)
                .sorted()
                .toList();

        if (!conflicts.isEmpty()) {
            String conflictList = conflicts.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));

            return "Seats already booked: " + conflictList;
        }

        // Rule 3: One customer can hold at most six confirmed seats
        // for this event across all their bookings.
        Long existingSeats =
                bookingRepository.countConfirmedSeatsForCustomer(
                        event.getId(),
                        customerEmail,
                        BookingStatus.CONFIRMED);

        long requestedTotal =
                (existingSeats == null ? 0L : existingSeats)
                        + requestedSeats.size();

        if (requestedTotal > MAX_SEATS_PER_CUSTOMER_PER_EVENT) {
            return "Customer already holds the maximum seats for this event";
        }

        return null;
    }

    private BigDecimal calculatePricePerSeat(
            Event event,
            int seatCount,
            LocalDate today) {

        BigDecimal price = event.getBasePrice();

        // Discount 1: 10% off when booking at least 30 days in advance.
        if (!event.getEventDate().isBefore(today.plusDays(30))) {
            price = price.multiply(EARLY_BIRD_MULTIPLIER);
        }

        // Discount 2: Additional 5% off for 4 or more seats.
        if (seatCount >= 4) {
            price = price.multiply(GROUP_MULTIPLIER);
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    private List<Integer> parseSeatNumbers(String seatNumbers) {
        if (seatNumbers == null || seatNumbers.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(seatNumbers.split(","))
                .map(String::trim)
                .map(Integer::valueOf)
                .toList();
    }

    private BookingResponse toResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getEvent().getId(),
                booking.getCustomerName(),
                booking.getCustomerEmail(),
                parseSeatNumbers(booking.getSeatNumbers()),
                booking.getSeats(),
                booking.getPricePerSeat(),
                booking.getTotalAmount(),
                booking.getStatus().name(),
                booking.getRejectionReason(),
                booking.getBookedOn());
    }
}
