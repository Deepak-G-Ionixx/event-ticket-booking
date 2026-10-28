package com.ionixx.eventticketbooking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
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

    // Existing POST /api/bookings business logic.
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {

        validateSeatNumbers(request.seatNumbers());

        Event event = eventRepository.findById(request.eventId())
                .orElseThrow(() ->
                        new EventNotFoundException(request.eventId()));

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

        return toResponse(bookingRepository.save(booking));
    }

    // Existing GET /api/bookings/{id}.
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Integer id) {

        Booking booking = bookingRepository
                .findBookingWithEventById(id)
                .orElseThrow(() -> new BookingNotFoundException(id));

        return toResponse(booking);
    }

    // GET /api/events/{id}/bookings?status=CONFIRMED
    @Transactional(readOnly = true)
    public List<BookingResponse> getEventBookings(
            Integer eventId,
            BookingStatus status) {

        ensureEventExists(eventId);

        List<Booking> bookings;

        if (status == null) {
            bookings = bookingRepository
                    .findAllByEvent_IdOrderByBookedOnDescIdDesc(eventId);
        } else {
            bookings = bookingRepository
                    .findAllByEvent_IdAndStatusOrderByBookedOnDescIdDesc(
                            eventId,
                            status);
        }

        return bookings.stream()
                .map(this::toResponse)
                .toList();
    }

    // GET /api/events/{id}/seats
    @Transactional(readOnly = true)
    public EventSeatMapResponse getEventSeatMap(Integer eventId) {

        Event event = ensureEventExists(eventId);

        List<Booking> confirmedBookings =
                bookingRepository.findAllByEvent_IdAndStatus(
                        eventId,
                        BookingStatus.CONFIRMED);

        Set<Integer> bookedSeats = new TreeSet<>();

        for (Booking booking : confirmedBookings) {
            bookedSeats.addAll(
                    parseSeatNumbers(booking.getSeatNumbers()));
        }

        List<Integer> availableSeats = new ArrayList<>();

        for (int seat = 1; seat <= event.getTotalSeats(); seat++) {
            if (!bookedSeats.contains(seat)) {
                availableSeats.add(seat);
            }
        }

        return new EventSeatMapResponse(
                event.getId(),
                event.getTotalSeats(),
                List.copyOf(bookedSeats),
                availableSeats);
    }

    // GET /api/events/{id}/summary
    @Transactional(readOnly = true)
    public EventSummaryResponse getEventSummary(Integer eventId) {

        Event event = ensureEventExists(eventId);

        List<Booking> confirmedBookings =
                bookingRepository.findAllByEvent_IdAndStatus(
                        eventId,
                        BookingStatus.CONFIRMED);

        List<Booking> rejectedBookings =
                bookingRepository.findAllByEvent_IdAndStatus(
                        eventId,
                        BookingStatus.REJECTED);

        int seatsSold = confirmedBookings.stream()
                .mapToInt(Booking::getSeats)
                .sum();

        BigDecimal totalRevenue = confirmedBookings.stream()
                .map(Booking::getTotalAmount)
                .filter(amount -> amount != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal exactOccupancy = BigDecimal.valueOf(seatsSold)
                .multiply(BigDecimal.valueOf(100))
                .divide(
                        BigDecimal.valueOf(event.getTotalSeats()),
                        6,
                        RoundingMode.HALF_UP);

        BigDecimal occupancyPct = exactOccupancy.setScale(
                1,
                RoundingMode.HALF_UP);

        String demandBand;

        if (exactOccupancy.compareTo(new BigDecimal("40")) < 0) {
            demandBand = "LOW";
        } else if (exactOccupancy.compareTo(new BigDecimal("75")) <= 0) {
            demandBand = "MEDIUM";
        } else {
            demandBand = "HIGH";
        }

        return new EventSummaryResponse(
                event.getId(),
                event.getName(),
                event.getTotalSeats(),
                seatsSold,
                event.getTotalSeats() - seatsSold,
                confirmedBookings.size(),
                rejectedBookings.size(),
                totalRevenue,
                occupancyPct,
                demandBand);
    }

    private Event ensureEventExists(Integer eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new EventNotFoundException(eventId));
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

        if (event.getEventDate().isBefore(today)) {
            return "Event is already over";
        }

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

        if (!event.getEventDate().isBefore(today.plusDays(30))) {
            price = price.multiply(EARLY_BIRD_MULTIPLIER);
        }

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
