package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingSeat;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.security.AuthenticatedUserService;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.BookingNotFoundException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.exception.InvalidBookingRequestException;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;
import com.ionixx.eventticketbooking.exception.SeatAlreadyBookedException;
import com.ionixx.eventticketbooking.exception.SeatOutOfRangeException;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.BookingSeatRepository;
import com.ionixx.eventticketbooking.repository.BookingStatusTotals;
import com.ionixx.eventticketbooking.repository.EventRepository;
import com.ionixx.eventticketbooking.security.SecurityTestSupport;

/**
 * Unit tests for every branch of BookingService.
 * Repositories and the authenticated-user lookup are mocked.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    private static final int EVENT_ID = 1;
    private static final int ORGANISER_ID = 10;
    private static final int OTHER_ORGANISER_ID = 11;
    private static final int CUSTOMER_ID = 20;
    private static final String ACCOUNT_EMAIL = "real.customer@example.com";

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private BookingSeatRepository bookingSeatRepository;

    @Mock
    private AuthenticatedUserService authenticatedUserService;

    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        bookingService = new BookingService(
                bookingRepository,
                eventRepository,
                bookingSeatRepository,
                authenticatedUserService);

        when(bookingSeatRepository.findConfirmedSeatNumbersByEventId(EVENT_ID))
                .thenReturn(List.of());
        when(bookingRepository.countConfirmedSeatsForCustomerId(
                EVENT_ID, CUSTOMER_ID, BookingStatus.CONFIRMED))
                .thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clear();
    }

    // ---- helpers ----------------------------------------------------------

    private static User customer(String name) {
        User user = new User(name, ACCOUNT_EMAIL, "hash", Role.CUSTOMER);
        ReflectionTestUtils.setField(user, "id", CUSTOMER_ID);
        return user;
    }

    private static Event event(int totalSeats, LocalDate date, String basePrice) {
        Event event = new Event(
                "Test Event",
                "Test Venue",
                EventCategory.TECH,
                date,
                totalSeats,
                new BigDecimal(basePrice),
                ORGANISER_ID);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    private static CreateBookingRequest request(String name, List<Integer> seats) {
        // The body names somebody else's email on purpose: it must be ignored.
        return new CreateBookingRequest(
                EVENT_ID, name, "someone.else@example.com", seats);
    }

    private void givenCustomer(User user) {
        when(authenticatedUserService.requireCurrentUserForUpdate(Role.CUSTOMER))
                .thenReturn(user);
    }

    private void givenEventForBooking(Event event) {
        when(eventRepository.findByIdForShare(EVENT_ID))
                .thenReturn(Optional.of(event));
    }

    private void givenSaveAssignsId(int id) {
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking booking = invocation.getArgument(0);
                    ReflectionTestUtils.setField(booking, "id", id);
                    return booking;
                });
    }

    private static Booking booking(Event event, String seatNumbers, int seats) {
        Booking booking = new Booking(
                event, "Jane Doe", ACCOUNT_EMAIL, seatNumbers, seats,
                BookingStatus.CONFIRMED, null, LocalDate.now(), CUSTOMER_ID);
        booking.confirm(new BigDecimal("100.00"), new BigDecimal("200.00"));
        ReflectionTestUtils.setField(booking, "id", 77);
        return booking;
    }

    /** Simple implementation of the aggregate projection for tests. */
    private record Totals(
            BookingStatus status,
            Long count,
            Long seats,
            BigDecimal amount) implements BookingStatusTotals {

        @Override
        public BookingStatus getBookingStatus() {
            return status;
        }

        @Override
        public Long getBookingCount() {
            return count;
        }

        @Override
        public Long getSeatCount() {
            return seats;
        }

        @Override
        public BigDecimal getRevenue() {
            return amount;
        }
    }

    // ---- createBooking: request validation ------------------------------

    @Test
    void createBookingRejectsMissingRequest() {
        assertThatThrownBy(() -> bookingService.createBooking(null))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("Booking request is required");

        verifyNoInteractions(authenticatedUserService, eventRepository);
    }

    static Stream<Arguments> invalidSeatLists() {
        return Stream.of(
                Arguments.of(null, "At least one seat number is required"),
                Arguments.of(List.of(), "At least one seat number is required"),
                Arguments.of(List.of(1, 2, 3, 4, 5, 6, 7),
                        "A booking can contain at most 6 seats"),
                Arguments.of(Arrays.asList(1, null),
                        "Seat numbers must be positive integers"),
                Arguments.of(List.of(0), "Seat numbers must be positive integers"),
                Arguments.of(List.of(-1), "Seat numbers must be positive integers"),
                Arguments.of(List.of(1, 1), "Duplicate seat numbers are not allowed"));
    }

    @ParameterizedTest
    @MethodSource("invalidSeatLists")
    void createBookingRejectsInvalidSeatListsBeforeTouchingTheDatabase(
            List<Integer> seats, String message) {

        assertThatThrownBy(() -> bookingService.createBooking(request("John Doe", seats)))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage(message);

        verifyNoInteractions(authenticatedUserService, eventRepository);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    // ---- createBooking: identity and event lookup ------------------------

    @Test
    void createBookingStopsWhenTheCallerIsNotAnAuthorisedCustomer() {
        when(authenticatedUserService.requireCurrentUserForUpdate(Role.CUSTOMER))
                .thenThrow(new ResourceAccessDeniedException());

        assertThatThrownBy(() -> bookingService.createBooking(request("John Doe", List.of(1))))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(eventRepository, never()).findByIdForShare(anyInt());
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBookingFailsWhenEventDoesNotExist() {
        givenCustomer(customer("Jane Doe"));
        when(eventRepository.findByIdForShare(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(request("John Doe", List.of(1))))
                .isInstanceOf(EventNotFoundException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void createBookingFailsWhenSeatIsBeyondTotalSeats() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(10, LocalDate.now().plusDays(10), "100.00"));

        assertThatThrownBy(() -> bookingService.createBooking(request("John Doe", List.of(11))))
                .isInstanceOf(SeatOutOfRangeException.class);

        verify(bookingRepository, never()).saveAndFlush(any());
    }

    // ---- createBooking: confirmed bookings -------------------------------

    @Test
    void confirmedBookingUsesAccountIdentityDiscountsAndReservesEverySeat() {
        givenCustomer(customer("Jane Doe"));
        // 60 days away -> early bird, 4 seats -> group discount.
        givenEventForBooking(event(50, LocalDate.now().plusDays(60), "1000.00"));
        givenSaveAssignsId(100);

        BookingResponse response = bookingService.createBooking(
                request("John Doe", List.of(4, 2, 3, 1)));

        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(saved.capture());

        // Owner and email come from the authenticated account, not the body.
        assertThat(saved.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(saved.getValue().getCustomerEmail()).isEqualTo(ACCOUNT_EMAIL);
        assertThat(saved.getValue().getCustomerName()).isEqualTo("John Doe");
        assertThat(saved.getValue().getSeatNumbers()).isEqualTo("1,2,3,4");

        // 1000 * 0.90 * 0.95 = 855.00 per seat.
        assertThat(response.id()).isEqualTo(100);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.pricePerSeat()).isEqualByComparingTo("855.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("3420.00");
        assertThat(response.seatNumbers()).containsExactly(1, 2, 3, 4);
        assertThat(response.customerEmail()).isEqualTo(ACCOUNT_EMAIL);
        assertThat(response.rejectionReason()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<BookingSeat>> seats = ArgumentCaptor.forClass(List.class);
        verify(bookingSeatRepository).saveAllAndFlush(seats.capture());
        assertThat(seats.getValue())
                .extracting(BookingSeat::getSeatNumber)
                .containsExactly(1, 2, 3, 4);
        assertThat(seats.getValue())
                .allSatisfy(seat -> assertThat(seat.getBooking()).isSameAs(saved.getValue()));
    }

    @Test
    void confirmedBookingWithoutDiscountsUsesTheBasePrice() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "1000.00"));
        givenSaveAssignsId(101);

        BookingResponse response = bookingService.createBooking(
                request("John Doe", List.of(5, 6)));

        assertThat(response.pricePerSeat()).isEqualByComparingTo("1000.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("2000.00");
    }

    @Test
    void missingSeatCountForCustomerIsTreatedAsZero() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "100.00"));
        givenSaveAssignsId(102);
        when(bookingRepository.countConfirmedSeatsForCustomerId(
                EVENT_ID, CUSTOMER_ID, BookingStatus.CONFIRMED))
                .thenReturn(null);

        assertThat(bookingService.createBooking(request("John Doe", List.of(1))).status())
                .isEqualTo("CONFIRMED");
    }

    // ---- createBooking: customer name -------------------------------------

    @Test
    void blankRequestNameFallsBackToTheAccountName() {
        givenCustomer(customer("Jane Account"));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "100.00"));
        givenSaveAssignsId(103);

        assertThat(bookingService.createBooking(request("   ", List.of(1))).customerName())
                .isEqualTo("Jane Account");
    }

    @Test
    void missingRequestNameUsesTheAccountNameShortenedToFiftyCharacters() {
        String longName = "A".repeat(60);
        givenCustomer(customer(longName));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "100.00"));
        givenSaveAssignsId(104);

        assertThat(bookingService.createBooking(request(null, List.of(1))).customerName())
                .isEqualTo("A".repeat(50));
    }

    // ---- createBooking: rejected bookings ---------------------------------

    @Test
    void bookingForPastEventIsRejectedAndReservesNoSeats() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(50, LocalDate.now().minusDays(1), "100.00"));
        givenSaveAssignsId(105);

        BookingResponse response = bookingService.createBooking(request("John Doe", List.of(1)));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("EVENT_DATE_PASSED");
        assertThat(response.pricePerSeat()).isNull();
        assertThat(response.totalAmount()).isNull();
        verify(bookingSeatRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void bookingForAlreadyTakenSeatIsRejected() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "100.00"));
        givenSaveAssignsId(106);
        when(bookingSeatRepository.findConfirmedSeatNumbersByEventId(EVENT_ID))
                .thenReturn(List.of(2, 9));

        BookingResponse response = bookingService.createBooking(request("John Doe", List.of(1, 2)));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("SEAT_ALREADY_BOOKED");
        verify(bookingSeatRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void bookingAboveTheCustomerLimitIsRejected() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "100.00"));
        givenSaveAssignsId(107);
        when(bookingRepository.countConfirmedSeatsForCustomerId(
                EVENT_ID, CUSTOMER_ID, BookingStatus.CONFIRMED))
                .thenReturn(5L);

        BookingResponse response = bookingService.createBooking(request("John Doe", List.of(1, 2)));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("CUSTOMER_SEAT_LIMIT_EXCEEDED");
        verify(bookingSeatRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void uniqueConstraintViolationBecomesSeatAlreadyBooked() {
        givenCustomer(customer("Jane Doe"));
        givenEventForBooking(event(50, LocalDate.now().plusDays(10), "100.00"));
        givenSaveAssignsId(108);
        when(bookingSeatRepository.saveAllAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("uk_event_seat_number"));

        assertThatThrownBy(() -> bookingService.createBooking(request("John Doe", List.of(3))))
                .isInstanceOf(SeatAlreadyBookedException.class)
                .hasMessage("One or more requested seats have already been booked");
    }

    // ---- getBookingById ---------------------------------------------------

    @Test
    void customerReadsOwnBooking() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        Booking booking = booking(event(50, LocalDate.now().plusDays(5), "100.00"), "1,2", 2);
        when(bookingRepository.findBookingWithEventById(77)).thenReturn(Optional.of(booking));

        BookingResponse response = bookingService.getBookingById(77);

        assertThat(response.id()).isEqualTo(77);
        assertThat(response.seatNumbers()).containsExactly(1, 2);
    }

    @Test
    void bookingWithoutStoredSeatsReturnsAnEmptySeatList() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        Booking booking = booking(event(50, LocalDate.now().plusDays(5), "100.00"), " ", 1);
        when(bookingRepository.findBookingWithEventById(77)).thenReturn(Optional.of(booking));

        assertThat(bookingService.getBookingById(77).seatNumbers()).isEmpty();
    }

    @Test
    void missingBookingIsReportedAsNotFound() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        when(bookingRepository.findBookingWithEventById(5)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getBookingById(5))
                .isInstanceOf(BookingNotFoundException.class);
    }

    @Test
    void anotherCustomerCannotReadTheBooking() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID + 1);
        Booking booking = booking(event(50, LocalDate.now().plusDays(5), "100.00"), "1", 1);
        when(bookingRepository.findBookingWithEventById(77)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.getBookingById(77))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    // ---- getEventBookings -------------------------------------------------

    @Test
    void ownerGetsAPageOfBookingsSortedNewestFirstByDefault() {
        SecurityTestSupport.actAs(Role.ORGANISER, ORGANISER_ID);
        Event event = event(50, LocalDate.now().plusDays(5), "100.00");
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.of(event));
        when(bookingRepository.findAllByEvent_Id(eq(EVENT_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking(event, "1,2", 2))));

        Page<BookingResponse> page = bookingService.getEventBookings(
                EVENT_ID, null, 0, 10, null);

        assertThat(page.getContent()).hasSize(1);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findAllByEvent_Id(eq(EVENT_ID), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("bookedOn"), Sort.Order.desc("id")));
    }

    @Test
    void ownerCanFilterByStatusAndChooseTheSortOrder() {
        SecurityTestSupport.actAs(Role.ORGANISER, ORGANISER_ID);
        Event event = event(50, LocalDate.now().plusDays(5), "100.00");
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.of(event));
        when(bookingRepository.findAllByEvent_IdAndStatus(
                eq(EVENT_ID), eq(BookingStatus.CONFIRMED), any(Pageable.class)))
                .thenReturn(Page.empty());

        bookingService.getEventBookings(
                EVENT_ID, BookingStatus.CONFIRMED, 2, 5, "total_amount,desc");

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findAllByEvent_IdAndStatus(
                eq(EVENT_ID), eq(BookingStatus.CONFIRMED), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.desc("totalAmount"), Sort.Order.desc("id")));
    }

    @Test
    void otherOrganiserIsDeniedBeforeAnyBookingIsRead() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER_ID);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, OTHER_ORGANISER_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.getEventBookings(
                EVENT_ID, null, 0, 10, null))
                .isInstanceOf(ResourceAccessDeniedException.class);

        // Even invalid paging is answered with 403, so nothing leaks.
        assertThatThrownBy(() -> bookingService.getEventBookings(
                EVENT_ID, null, -1, 0, "bogus"))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(bookingRepository, never()).findAllByEvent_Id(any(), any());
    }

    @Test
    void missingEventIsReportedAsNotFound() {
        SecurityTestSupport.actAs(Role.ORGANISER, ORGANISER_ID);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> bookingService.getEventBookings(
                EVENT_ID, null, 0, 10, null))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void invalidPagingForOwnedEventIsRejected() {
        SecurityTestSupport.actAs(Role.ORGANISER, ORGANISER_ID);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.of(event(50, LocalDate.now().plusDays(5), "100.00")));

        assertThatThrownBy(() -> bookingService.getEventBookings(
                EVENT_ID, null, 0, 101, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page size must be between 1 and 100");

        assertThatThrownBy(() -> bookingService.getEventBookings(
                EVENT_ID, null, 0, 10, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Unsupported sort property 'password'");
    }

    // ---- getMyBookings ----------------------------------------------------

    @Test
    void myBookingsAreQueriedWithTheAuthenticatedCustomerId() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        Event event = event(50, LocalDate.now().plusDays(5), "100.00");
        when(bookingRepository.findAllByCustomerId(eq(CUSTOMER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(booking(event, "3", 1))));

        Page<BookingResponse> page = bookingService.getMyBookings(null, 0, 10, "seats");

        assertThat(page.getContent()).extracting(BookingResponse::id).containsExactly(77);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(bookingRepository).findAllByCustomerId(eq(CUSTOMER_ID), pageable.capture());
        assertThat(pageable.getValue().getSort()).isEqualTo(
                Sort.by(Sort.Order.asc("seats"), Sort.Order.desc("id")));
    }

    @Test
    void myBookingsCanBeFilteredByStatus() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        when(bookingRepository.findAllByCustomerIdAndStatus(
                eq(CUSTOMER_ID), eq(BookingStatus.REJECTED), any(Pageable.class)))
                .thenReturn(Page.empty());

        assertThat(bookingService.getMyBookings(BookingStatus.REJECTED, 0, 5, null))
                .isEmpty();
        verify(bookingRepository, never()).findAllByCustomerId(any(), any());
    }

    @Test
    void myBookingsRejectsInvalidPaging() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);

        assertThatThrownBy(() -> bookingService.getMyBookings(null, -1, 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Page number must be zero or greater");
    }

    // ---- getEventSeatMap ----------------------------------------------------

    @Test
    void seatMapListsBookedAndAvailableSeats() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(event(10, LocalDate.now().plusDays(5), "100.00")));
        when(bookingSeatRepository.findConfirmedSeatNumbersByEventId(EVENT_ID))
                .thenReturn(List.of(3, 2));

        EventSeatMapResponse seatMap = bookingService.getEventSeatMap(EVENT_ID);

        assertThat(seatMap.totalSeats()).isEqualTo(10);
        assertThat(seatMap.bookedSeats()).containsExactly(2, 3);
        assertThat(seatMap.availableSeats()).containsExactly(1, 4, 5, 6, 7, 8, 9, 10);
    }

    @Test
    void seatMapForMissingEventIsNotFound() {
        SecurityTestSupport.actAs(Role.CUSTOMER, CUSTOMER_ID);
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.getEventSeatMap(EVENT_ID))
                .isInstanceOf(EventNotFoundException.class);
    }

    // ---- getEventSummary ----------------------------------------------------

    private void givenOwnedEvent(Event event) {
        SecurityTestSupport.actAs(Role.ORGANISER, ORGANISER_ID);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, ORGANISER_ID))
                .thenReturn(Optional.of(event));
    }

    @Test
    void summaryUsesAggregatedTotalsForMediumDemand() {
        givenOwnedEvent(event(10, LocalDate.now().plusDays(5), "100.00"));
        when(bookingRepository.summarizeByStatus(EVENT_ID)).thenReturn(List.of(
                new Totals(BookingStatus.CONFIRMED, 2L, 4L, new BigDecimal("400")),
                new Totals(BookingStatus.REJECTED, 1L, 2L, null)));

        EventSummaryResponse summary = bookingService.getEventSummary(EVENT_ID);

        assertThat(summary.eventId()).isEqualTo(EVENT_ID);
        assertThat(summary.eventName()).isEqualTo("Test Event");
        assertThat(summary.seatsSold()).isEqualTo(4);
        assertThat(summary.seatsAvailable()).isEqualTo(6);
        assertThat(summary.confirmedBookings()).isEqualTo(2);
        assertThat(summary.rejectedBookings()).isEqualTo(1);
        assertThat(summary.totalRevenue()).isEqualByComparingTo("400.00");
        assertThat(summary.totalRevenue().scale()).isEqualTo(2);
        assertThat(summary.occupancyPct()).isEqualByComparingTo("40.0");
        assertThat(summary.demandBand()).isEqualTo("MEDIUM");
        verify(bookingRepository, never()).findAllByEvent_Id(any(), any());
    }

    @Test
    void summaryWithoutBookingsIsLowDemand() {
        givenOwnedEvent(event(10, LocalDate.now().plusDays(5), "100.00"));
        when(bookingRepository.summarizeByStatus(EVENT_ID)).thenReturn(List.of());

        EventSummaryResponse summary = bookingService.getEventSummary(EVENT_ID);

        assertThat(summary.seatsSold()).isZero();
        assertThat(summary.confirmedBookings()).isZero();
        assertThat(summary.rejectedBookings()).isZero();
        assertThat(summary.totalRevenue()).isEqualByComparingTo("0.00");
        assertThat(summary.occupancyPct()).isEqualByComparingTo("0.0");
        assertThat(summary.demandBand()).isEqualTo("LOW");
    }

    @Test
    void summaryAboveSeventyFivePercentIsHighDemand() {
        givenOwnedEvent(event(10, LocalDate.now().plusDays(5), "100.00"));
        when(bookingRepository.summarizeByStatus(EVENT_ID)).thenReturn(List.of(
                new Totals(BookingStatus.CONFIRMED, 3L, 8L, null)));

        EventSummaryResponse summary = bookingService.getEventSummary(EVENT_ID);

        assertThat(summary.occupancyPct()).isEqualByComparingTo("80.0");
        assertThat(summary.demandBand()).isEqualTo("HIGH");
        assertThat(summary.totalRevenue()).isEqualByComparingTo("0.00");
    }

    @Test
    void summaryForEventWithoutSeatsReportsZeroOccupancy() {
        givenOwnedEvent(event(0, LocalDate.now().plusDays(5), "100.00"));
        when(bookingRepository.summarizeByStatus(EVENT_ID)).thenReturn(List.of());

        EventSummaryResponse summary = bookingService.getEventSummary(EVENT_ID);

        assertThat(summary.occupancyPct()).isEqualByComparingTo("0.0");
        assertThat(summary.demandBand()).isEqualTo("LOW");
    }

    @Test
    void summaryOfAnotherOrganisersEventIsDenied() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER_ID);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, OTHER_ORGANISER_ID))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.getEventSummary(EVENT_ID))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(bookingRepository, never()).summarizeByStatus(any());
    }
}
