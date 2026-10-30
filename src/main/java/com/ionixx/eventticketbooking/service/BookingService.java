package com.ionixx.eventticketbooking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingSeat;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.BookingNotFoundException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.exception.InvalidBookingRequestException;
import com.ionixx.eventticketbooking.exception.SeatAlreadyBookedException;
import com.ionixx.eventticketbooking.exception.SeatOutOfRangeException;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.BookingSeatRepository;
import com.ionixx.eventticketbooking.repository.BookingStatusTotals;
import com.ionixx.eventticketbooking.repository.EventRepository;
import com.ionixx.eventticketbooking.security.AccessRules;
import com.ionixx.eventticketbooking.security.CurrentUser;
import com.ionixx.eventticketbooking.security.UserPrincipal;
import com.ionixx.eventticketbooking.security.AuthenticatedUserService;
@Service
public class BookingService {

    private static final int MAX_SEATS_PER_BOOKING = 6;
    private static final int MAX_SEATS_PER_CUSTOMER_PER_EVENT = 6;

    // Matches booking.customer_name VARCHAR(50).
    private static final int MAX_CUSTOMER_NAME_LENGTH = 50;

    private static final BigDecimal EARLY_BIRD_MULTIPLIER =
            new BigDecimal("0.90");

    private static final BigDecimal GROUP_MULTIPLIER =
            new BigDecimal("0.95");

    /**
     * Properties a client may sort booking lists by (API name -> entity property).
     * Both camelCase and the snake_case JSON names are accepted.
     */
    static final Map<String, String> BOOKING_SORT_PROPERTIES = Map.of(
            "id", "id",
            "bookedOn", "bookedOn",
            "booked_on", "bookedOn",
            "seats", "seats",
            "totalAmount", "totalAmount",
            "total_amount", "totalAmount",
            "status", "status");

    // Newest bookings first. ID provides a consistent tie-breaker.
    private static final Sort DEFAULT_BOOKING_SORT = Sort.by(
            Sort.Order.desc("bookedOn"),
            Sort.Order.desc("id"));

    private static final Sort.Order BOOKING_TIE_BREAKER = Sort.Order.desc("id");

    private final BookingRepository bookingRepository;
    private final EventRepository eventRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final AuthenticatedUserService authenticatedUserService;

    public BookingService(
            BookingRepository bookingRepository,
            EventRepository eventRepository,
            BookingSeatRepository bookingSeatRepository,
            AuthenticatedUserService authenticatedUserService) {

        this.bookingRepository = bookingRepository;
        this.eventRepository = eventRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.authenticatedUserService = authenticatedUserService;
    }

    /**
     * Creates a booking for the currently authenticated customer.
     *
     * Transaction boundary (everything commits together or rolls back together):
     * 1. Validate the request (no database access).
     * 2. Load and lock the authenticated customer's account.
     * 3. Load the event with a shared lock and validate the requested seats.
     * 4. Apply the business rules and calculate the price.
     * 5. Insert the booking and one booking_seat row per seat. The unique
     *    constraint (event_id, seat_number) is the final guard against two
     *    concurrent requests for the same seat.
     *
     * The customer id and email always come from the authenticated account.
     * A customer email in the request body is ignored, so nobody can create
     * a booking in another customer's name.
     */
    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {

        validateBookingRequest(request);
        validateSeatNumbers(request.seatNumbers());

        // Identity from the JWT, re-checked against the database and row-locked.
        User customer = authenticatedUserService
                .requireCurrentUserForUpdate(Role.CUSTOMER);

        Event event = eventRepository.findByIdForShare(request.eventId())
                .orElseThrow(() -> new EventNotFoundException(request.eventId()));

        // Every requested seat must belong to the selected event.
        validateSeatNumbersAgainstEvent(request.seatNumbers(), event);

        // Sort seat numbers so the stored value and response are consistent.
        // Sorted inserts also give every transaction the same lock order.
        List<Integer> sortedSeatNumbers = request.seatNumbers().stream()
                .sorted()
                .toList();

        LocalDate today = LocalDate.now();

        String rejectionReason = determineRejectionReason(
                event,
                customer.getId(),
                sortedSeatNumbers,
                today);

        Booking booking = new Booking(
                event,
                resolveCustomerName(request.customerName(), customer),
                customer.getEmail(),
                sortedSeatNumbers.stream()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")),
                sortedSeatNumbers.size(),
                rejectionReason == null
                        ? BookingStatus.CONFIRMED
                        : BookingStatus.REJECTED,
                rejectionReason,
                today,
                customer.getId());

        if (rejectionReason == null) {
            BigDecimal pricePerSeat = calculatePricePerSeat(
                    event,
                    sortedSeatNumbers.size(),
                    today);

            BigDecimal totalAmount = pricePerSeat.multiply(
                            BigDecimal.valueOf(sortedSeatNumbers.size()))
                    .setScale(2, RoundingMode.HALF_UP);

            booking.confirm(pricePerSeat, totalAmount);
        } else {
            booking.reject(rejectionReason);
        }

        Booking savedBooking = bookingRepository.saveAndFlush(booking);

        // Only confirmed bookings reserve seats.
        if (savedBooking.getStatus() == BookingStatus.CONFIRMED) {

            List<BookingSeat> bookingSeats = sortedSeatNumbers.stream()
                    .map(seatNumber -> new BookingSeat(
                            event,
                            savedBooking,
                            seatNumber))
                    .toList();

            try {
                // The database constraint protects against concurrent bookings.
                bookingSeatRepository.saveAllAndFlush(bookingSeats);
            } catch (DataIntegrityViolationException exception) {
                // The exception leaves the method, so the whole transaction
                // (including the booking row above) is rolled back.
                throw new SeatAlreadyBookedException(
                        "One or more requested seats have already been booked");
            }
        }

        return toResponse(savedBooking);
    }

    /**
     * Returns one booking after checking that the current user is allowed
     * to view it.
     */
    @Transactional(readOnly = true)
    public BookingResponse getBookingById(Integer bookingId) {

        UserPrincipal user = CurrentUser.require();

        Booking booking = bookingRepository
                .findBookingWithEventById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));

        // Prevent users from viewing bookings they are not authorized to access.
        AccessRules.requireCanViewBooking(user, booking);

        return toResponse(booking);
    }

    /**
     * Returns one page of bookings for an event owned by the current organiser.
     *
     * The ownership check runs before the paging parameters are validated and
     * before any booking is read, so no page (and no error message) reveals
     * anything about another organiser's event.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getEventBookings(
            Integer eventId,
            BookingStatus status,
            int page,
            int size,
            String sort) {

        UserPrincipal user = CurrentUser.require();

        OwnedEvents.require(
                user,
                eventId,
                organiserId -> eventRepository.findByIdAndOrganiserId(
                        eventId, organiserId),
                eventRepository);

        Pageable pageable = bookingPageRequest(page, size, sort);

        Page<Booking> bookingPage = status == null
                ? bookingRepository.findAllByEvent_Id(eventId, pageable)
                : bookingRepository.findAllByEvent_IdAndStatus(
                eventId,
                status,
                pageable);

        return bookingPage.map(this::toResponse);
    }

    /**
     * Returns one page of the logged-in customer's own bookings.
     * The customer ID comes from the authenticated user, never the request.
     */
    @Transactional(readOnly = true)
    public Page<BookingResponse> getMyBookings(
            BookingStatus status,
            int page,
            int size,
            String sort) {

        UserPrincipal user = CurrentUser.require();

        Pageable pageable = bookingPageRequest(page, size, sort);

        Page<Booking> bookingPage = status == null
                ? bookingRepository.findAllByCustomerId(user.id(), pageable)
                : bookingRepository.findAllByCustomerIdAndStatus(
                user.id(),
                status,
                pageable);

        return bookingPage.map(this::toResponse);
    }

    /**
     * Returns confirmed and available seats for an event.
     */
    @Transactional(readOnly = true)
    public EventSeatMapResponse getEventSeatMap(Integer eventId) {

        UserPrincipal user = CurrentUser.require();

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        // Enforce the existing event visibility rules.
        AccessRules.requireCanViewEvent(user, event);

        List<Integer> bookedSeats =
                bookingSeatRepository.findConfirmedSeatNumbersByEventId(eventId);

        Set<Integer> bookedSeatSet = new TreeSet<>(bookedSeats);

        List<Integer> availableSeats = new ArrayList<>();

        for (int seatNumber = 1;
             seatNumber <= event.getTotalSeats();
             seatNumber++) {

            if (!bookedSeatSet.contains(seatNumber)) {
                availableSeats.add(seatNumber);
            }
        }

        return new EventSeatMapResponse(
                eventId,
                event.getTotalSeats(),
                new ArrayList<>(bookedSeatSet),
                availableSeats);
    }

    /**
     * Returns summary information for an event.
     * Only the event's organizer may view this summary.
     *
     * Counts and sums are calculated by one GROUP BY query instead of
     * loading every booking of the event into memory.
     */
    @Transactional(readOnly = true)
    public EventSummaryResponse getEventSummary(Integer eventId) {

        UserPrincipal user = CurrentUser.require();

        Event event = OwnedEvents.require(
                user,
                eventId,
                organiserId -> eventRepository.findByIdAndOrganiserId(
                        eventId, organiserId),
                eventRepository);

        long confirmedBookings = 0;
        long rejectedBookings = 0;
        long seatsSold = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;

        for (BookingStatusTotals totals
                : bookingRepository.summarizeByStatus(eventId)) {

            if (totals.getBookingStatus() == BookingStatus.CONFIRMED) {
                confirmedBookings = Objects.requireNonNullElse(
                        totals.getBookingCount(), 0L);
                seatsSold = Objects.requireNonNullElse(
                        totals.getSeatCount(), 0L);
                totalRevenue = Objects.requireNonNullElse(
                        totals.getRevenue(), BigDecimal.ZERO);
            } else {
                rejectedBookings = Objects.requireNonNullElse(
                        totals.getBookingCount(), 0L);
            }
        }

        totalRevenue = totalRevenue.setScale(2, RoundingMode.HALF_UP);

        int totalSeats = event.getTotalSeats();
        int soldSeats = Math.toIntExact(seatsSold);
        int availableSeats = totalSeats - soldSeats;

        // Occupancy is expressed as a percentage rounded to one decimal place.
        BigDecimal occupancyPct = BigDecimal.ZERO.setScale(1);

        if (totalSeats > 0) {
            occupancyPct = BigDecimal.valueOf(soldSeats)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            BigDecimal.valueOf(totalSeats),
                            1,
                            RoundingMode.HALF_UP);
        }

        String demandBand;

        if (occupancyPct.compareTo(new BigDecimal("40.0")) < 0) {
            demandBand = "LOW";
        } else if (occupancyPct.compareTo(new BigDecimal("75.0")) <= 0) {
            demandBand = "MEDIUM";
        } else {
            demandBand = "HIGH";
        }

        return new EventSummaryResponse(
                eventId,
                event.getName(),
                totalSeats,
                soldSeats,
                availableSeats,
                Math.toIntExact(confirmedBookings),
                Math.toIntExact(rejectedBookings),
                totalRevenue,
                occupancyPct,
                demandBand);
    }

    /**
     * Builds a validated page request for booking lists.
     */
    private Pageable bookingPageRequest(int page, int size, String sort) {
        return PagingSupport.pageRequest(
                page,
                size,
                sort,
                BOOKING_SORT_PROPERTIES,
                DEFAULT_BOOKING_SORT,
                BOOKING_TIE_BREAKER);
    }

    /**
     * Validates that a booking request was provided.
     */
    private void validateBookingRequest(CreateBookingRequest request) {

        if (request == null) {
            throw new InvalidBookingRequestException(
                    "INVALID_BOOKING_REQUEST",
                    "Booking request is required");
        }
    }

    /**
     * Validates the seat list before looking up or saving a booking.
     */
    private void validateSeatNumbers(List<Integer> requestedSeatNumbers) {

        if (requestedSeatNumbers == null || requestedSeatNumbers.isEmpty()) {
            throw new InvalidBookingRequestException(
                    "INVALID_BOOKING_REQUEST",
                    "At least one seat number is required");
        }

        if (requestedSeatNumbers.size() > MAX_SEATS_PER_BOOKING) {
            throw new InvalidBookingRequestException(
                    "INVALID_BOOKING_REQUEST",
                    "A booking can contain at most 6 seats");
        }

        if (requestedSeatNumbers.stream()
                .anyMatch(seat -> seat == null || seat <= 0)) {
            throw new InvalidBookingRequestException(
                    "INVALID_BOOKING_REQUEST",
                    "Seat numbers must be positive integers");
        }

        if (requestedSeatNumbers.stream().distinct().count()
                != requestedSeatNumbers.size()) {
            throw new InvalidBookingRequestException(
                    "INVALID_BOOKING_REQUEST",
                    "Duplicate seat numbers are not allowed");
        }
    }

    /**
     * Checks that requested seat numbers do not exceed the event capacity.
     */
    private void validateSeatNumbersAgainstEvent(
            List<Integer> requestedSeatNumbers,
            Event event) {

        for (Integer seatNumber : requestedSeatNumbers) {
            if (seatNumber > event.getTotalSeats()) {
                // SeatOutOfRangeException expects the invalid seat number.
                throw new SeatOutOfRangeException(seatNumber);
            }
        }
    }

    /**
     * The name printed on the booking. The request may supply a display
     * name; otherwise the registered account name is used (shortened to
     * the column length).
     */
    private static String resolveCustomerName(
            String requestedName,
            User customer) {

        if (requestedName != null && !requestedName.isBlank()) {
            return requestedName;
        }

        String accountName = customer.getName();

        return accountName.length() > MAX_CUSTOMER_NAME_LENGTH
                ? accountName.substring(0, MAX_CUSTOMER_NAME_LENGTH)
                : accountName;
    }

    /**
     * Returns a rejection reason if the booking violates a booking rule.
     * A null result means the booking can be confirmed.
     */
    private String determineRejectionReason(
            Event event,
            Integer customerId,
            List<Integer> requestedSeatNumbers,
            LocalDate today) {

        if (event.getEventDate().isBefore(today)) {
            return "EVENT_DATE_PASSED";
        }

        Integer eventId = event.getId();

        // Friendly pre-check. The unique constraint still decides races.
        List<Integer> alreadyBookedSeats =
                bookingSeatRepository.findConfirmedSeatNumbersByEventId(eventId);

        Set<Integer> bookedSeatSet = new TreeSet<>(alreadyBookedSeats);

        boolean seatAlreadyBooked = requestedSeatNumbers.stream()
                .anyMatch(bookedSeatSet::contains);

        if (seatAlreadyBooked) {
            return "SEAT_ALREADY_BOOKED";
        }

        // Count only confirmed seats belonging to the authenticated customer.
        // The customer's row is locked, so this count cannot change underneath us.
        Long alreadyBookedSeatsByCustomer =
                bookingRepository.countConfirmedSeatsForCustomerId(
                        eventId,
                        customerId,
                        BookingStatus.CONFIRMED);

        long confirmedSeatCount = Objects.requireNonNullElse(
                alreadyBookedSeatsByCustomer, 0L);

        // Include the seats in this request when enforcing the six-seat limit.
        if (confirmedSeatCount + requestedSeatNumbers.size()
                > MAX_SEATS_PER_CUSTOMER_PER_EVENT) {
            return "CUSTOMER_SEAT_LIMIT_EXCEEDED";
        }

        return null;
    }

    /**
     * Calculates the per-seat price after applying applicable discounts.
     */
    private BigDecimal calculatePricePerSeat(
            Event event,
            int requestedSeatCount,
            LocalDate today) {

        BigDecimal price = event.getBasePrice();

        // Apply the early-bird discount when the event is at least 30 days away.
        if (!event.getEventDate().isBefore(today.plusDays(30))) {
            price = price.multiply(EARLY_BIRD_MULTIPLIER);
        }

        // Apply the group discount when four or more seats are booked.
        if (requestedSeatCount >= 4) {
            price = price.multiply(GROUP_MULTIPLIER);
        }

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Converts comma-separated seat numbers stored in the database
     * into a list of integers.
     */
    private List<Integer> parseSeatNumbers(String seatNumbers) {

        if (seatNumbers == null || seatNumbers.isBlank()) {
            return List.of();
        }

        return Arrays.stream(seatNumbers.split(","))
                .map(String::trim)
                .map(Integer::valueOf)
                .toList();
    }

    /**
     * Converts a Booking entity into the API response DTO.
     * Only the event id is read from the association, which Hibernate
     * answers without loading the event.
     */
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
