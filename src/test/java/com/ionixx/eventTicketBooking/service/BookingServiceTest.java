package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.dto.EventSeatMapResponse;
import com.ionixx.eventticketbooking.dto.EventSummaryResponse;
import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.exception.BookingNotFoundException;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.exception.InvalidBookingRequestException;
import com.ionixx.eventticketbooking.exception.SeatOutOfRangeException;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.EventRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BookingServiceTest {

    private static final int EVENT_ID = 1;
    private static final String EMAIL = "john@example.com";

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private EventRepository eventRepository;

    private BookingService service;

    @BeforeEach
    void setUp() {
        service = new BookingService(bookingRepository, eventRepository);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Event event(int totalSeats, LocalDate date, String basePrice) {
        Event event = new Event(
                "Rock Night",
                "Chennai Hall",
                EventCategory.MUSIC,
                date,
                totalSeats,
                new BigDecimal(basePrice));
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    private static Booking confirmedBooking(
            Event event, String seatNumbers, int seats, String totalAmount) {

        Booking booking = new Booking(
                event, "Test User", "other@example.com",
                seatNumbers, seats, BookingStatus.CONFIRMED,
                null, LocalDate.now());

        if (totalAmount != null) {
            booking.confirm(new BigDecimal("100.00"), new BigDecimal(totalAmount));
        }
        return booking;
    }

    private static Booking rejectedBooking(Event event) {
        Booking booking = new Booking(
                event, "Test User", "other@example.com",
                "9", 1, BookingStatus.REJECTED,
                "Some reason", LocalDate.now());
        booking.reject("Some reason");
        return booking;
    }

    private static List<Integer> seats(int count) {
        return IntStream.rangeClosed(1, count).boxed().toList();
    }

    private static CreateBookingRequest request(List<Integer> seatNumbers) {
        return new CreateBookingRequest(
                EVENT_ID, "John Doe", "John@Example.com", seatNumbers);
    }

    private void givenEvent(Event event) {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.of(event));
    }

    private void givenNoExistingBookings(int existingSeatsForCustomer) {
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED)).thenReturn(List.of());
        when(bookingRepository.countConfirmedSeatsForCustomer(
                EVENT_ID, EMAIL, BookingStatus.CONFIRMED))
                .thenReturn((long) existingSeatsForCustomer);
    }

    private void givenSaveReturnsArgument() {
        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    // ------------------------------------------------------------------
    // createBooking: seat validation
    // ------------------------------------------------------------------

    @Test
    void createBookingRejectsNullSeatList() {
        assertThatThrownBy(() -> service.createBooking(request(null)))
                .isInstanceOfSatisfying(InvalidBookingRequestException.class, ex -> {
                    assertThat(ex.getMessage())
                            .isEqualTo("At least one seat number is required");
                    assertThat(ex.getField()).isEqualTo("seat_numbers");
                });
        verify(eventRepository, never()).findById(any());
    }

    @Test
    void createBookingRejectsEmptySeatList() {
        assertThatThrownBy(() -> service.createBooking(request(List.of())))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("At least one seat number is required");
    }

    @Test
    void createBookingRejectsMoreThanSixSeats() {
        assertThatThrownBy(() -> service.createBooking(request(seats(7))))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("A booking can contain at most 6 seats");
    }

    @Test
    void createBookingRejectsNullSeatNumber() {
        List<Integer> withNull = Arrays.asList(1, null);

        assertThatThrownBy(() -> service.createBooking(request(withNull)))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("Seat numbers must be positive integers");
    }

    @Test
    void createBookingRejectsZeroSeatNumber() {
        assertThatThrownBy(() -> service.createBooking(request(List.of(0, 1))))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("Seat numbers must be positive integers");
    }

    @Test
    void createBookingRejectsNegativeSeatNumber() {
        assertThatThrownBy(() -> service.createBooking(request(List.of(-3))))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("Seat numbers must be positive integers");
    }

    @Test
    void createBookingRejectsDuplicateSeatNumbers() {
        assertThatThrownBy(() -> service.createBooking(request(List.of(2, 3, 2))))
                .isInstanceOf(InvalidBookingRequestException.class)
                .hasMessage("Duplicate seat numbers are not allowed");
    }

    // ------------------------------------------------------------------
    // createBooking: event lookups
    // ------------------------------------------------------------------

    @Test
    void createBookingFailsWhenEventDoesNotExist() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBooking(request(List.of(1))))
                .isInstanceOf(EventNotFoundException.class)
                .hasMessageContaining("1");
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingFailsWhenSeatIsBeyondTotalSeats() {
        givenEvent(event(10, LocalDate.now().plusDays(60), "1000.00"));

        assertThatThrownBy(() -> service.createBooking(request(List.of(1, 11))))
                .isInstanceOfSatisfying(SeatOutOfRangeException.class,
                        ex -> assertThat(ex.getSeatNumber()).isEqualTo(11));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void createBookingAllowsLastSeatOfEvent() {
        givenEvent(event(10, LocalDate.now().plusDays(60), "1000.00"));
        givenNoExistingBookings(0);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(10)));

        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    // ------------------------------------------------------------------
    // createBooking: rejections
    // ------------------------------------------------------------------

    @Test
    void createBookingIsRejectedWhenEventIsAlreadyOver() {
        givenEvent(event(50, LocalDate.now().minusDays(1), "1000.00"));
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(1, 2)));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("Event is already over");
        assertThat(response.pricePerSeat()).isNull();
        assertThat(response.totalAmount()).isNull();
        verify(bookingRepository, never()).countConfirmedSeatsForCustomer(
                any(), any(), any());
    }

    @Test
    void createBookingIsRejectedWhenSeatsAreAlreadyBooked() {
        Event event = event(50, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED))
                .thenReturn(List.of(
                        confirmedBooking(event, "3,4", 2, "200.00"),
                        confirmedBooking(event, "5", 1, "100.00")));
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(5, 4, 9)));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason()).isEqualTo("Seats already booked: 4, 5");
        assertThat(response.seatNumbers()).containsExactly(4, 5, 9);
    }

    @Test
    void createBookingIsRejectedWhenCustomerWouldExceedSixSeats() {
        givenEvent(event(50, LocalDate.now().plusDays(60), "1000.00"));
        givenNoExistingBookings(5);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(10, 11)));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.rejectionReason())
                .isEqualTo("Customer already holds the maximum seats for this event");
    }

    @Test
    void createBookingIsAllowedWhenCustomerReachesExactlySixSeats() {
        givenEvent(event(50, LocalDate.now().plusDays(60), "1000.00"));
        givenNoExistingBookings(4);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(10, 11)));

        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void createBookingTreatsNullCustomerSeatCountAsZero() {
        givenEvent(event(50, LocalDate.now().plusDays(60), "1000.00"));
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED)).thenReturn(List.of());
        when(bookingRepository.countConfirmedSeatsForCustomer(
                EVENT_ID, EMAIL, BookingStatus.CONFIRMED)).thenReturn(null);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(1)));

        assertThat(response.status()).isEqualTo("CONFIRMED");
    }

    // ------------------------------------------------------------------
    // createBooking: confirmed bookings and pricing
    // ------------------------------------------------------------------

    @Test
    void createBookingStoresNormalisedCustomerAndSortedSeats() {
        givenEvent(event(50, LocalDate.now().plusDays(60), "1000.00"));
        givenNoExistingBookings(0);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(5, 2, 3)));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).save(captor.capture());
        Booking saved = captor.getValue();

        assertThat(saved.getSeatNumbers()).isEqualTo("2,3,5");
        assertThat(saved.getSeats()).isEqualTo(3);
        assertThat(saved.getCustomerEmail()).isEqualTo(EMAIL);
        assertThat(saved.getCustomerName()).isEqualTo("John Doe");
        assertThat(saved.getBookedOn()).isEqualTo(LocalDate.now());
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CONFIRMED);

        assertThat(response.seatNumbers()).containsExactly(2, 3, 5);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.rejectionReason()).isNull();
    }

    @ParameterizedTest
    @CsvSource({
            "29, 1, 1000.00",
            "30, 1, 900.00",
            "90, 3, 900.00",
            "29, 3, 1000.00",
            "29, 4, 950.00",
            "30, 4, 855.00",
            "5, 6, 950.00",
            "120, 6, 855.00"
    })
    void createBookingAppliesEarlyBirdAndGroupDiscounts(
            int daysUntilEvent, int seatCount, String expectedPricePerSeat) {

        givenEvent(event(100, LocalDate.now().plusDays(daysUntilEvent), "1000.00"));
        givenNoExistingBookings(0);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(seats(seatCount)));

        BigDecimal expectedPrice = new BigDecimal(expectedPricePerSeat);
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.pricePerSeat()).isEqualByComparingTo(expectedPrice);
        assertThat(response.totalAmount()).isEqualByComparingTo(
                expectedPrice.multiply(BigDecimal.valueOf(seatCount)));
    }

    @Test
    void createBookingRoundsPriceToTwoDecimals() {
        givenEvent(event(100, LocalDate.now().plusDays(60), "99.99"));
        givenNoExistingBookings(0);
        givenSaveReturnsArgument();

        BookingResponse response = service.createBooking(request(List.of(1)));

        // 99.99 * 0.90 = 89.991 -> 89.99
        assertThat(response.pricePerSeat()).isEqualTo(new BigDecimal("89.99"));
        assertThat(response.totalAmount()).isEqualTo(new BigDecimal("89.99"));
    }

    // ------------------------------------------------------------------
    // getBookingById
    // ------------------------------------------------------------------

    @Test
    void getBookingByIdReturnsMappedResponse() {
        Event event = event(50, LocalDate.now().plusDays(60), "1000.00");
        Booking booking = confirmedBooking(event, "3,4", 2, "200.00");
        ReflectionTestUtils.setField(booking, "id", 77);
        when(bookingRepository.findBookingWithEventById(77))
                .thenReturn(Optional.of(booking));

        BookingResponse response = service.getBookingById(77);

        assertThat(response.id()).isEqualTo(77);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.customerName()).isEqualTo("Test User");
        assertThat(response.customerEmail()).isEqualTo("other@example.com");
        assertThat(response.seatNumbers()).containsExactly(3, 4);
        assertThat(response.seats()).isEqualTo(2);
        assertThat(response.pricePerSeat()).isEqualByComparingTo("100.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("200.00");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.bookedOn()).isEqualTo(LocalDate.now());
    }

    @Test
    void getBookingByIdThrowsWhenMissing() {
        when(bookingRepository.findBookingWithEventById(5))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getBookingById(5))
                .isInstanceOf(BookingNotFoundException.class)
                .hasMessage("Booking not found with id: 5");
    }

    // ------------------------------------------------------------------
    // getEventBookings
    // ------------------------------------------------------------------

    @Test
    void getEventBookingsThrowsWhenEventMissing() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventBookings(EVENT_ID, null))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void getEventBookingsWithoutStatusReturnsAllBookings() {
        Event event = event(50, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        when(bookingRepository.findAllByEvent_IdOrderByBookedOnDescIdDesc(EVENT_ID))
                .thenReturn(List.of(
                        confirmedBooking(event, "1", 1, "100.00"),
                        rejectedBooking(event)));

        List<BookingResponse> responses = service.getEventBookings(EVENT_ID, null);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).status()).isEqualTo("CONFIRMED");
        assertThat(responses.get(1).status()).isEqualTo("REJECTED");
        verify(bookingRepository, never())
                .findAllByEvent_IdAndStatusOrderByBookedOnDescIdDesc(any(), any());
    }

    @Test
    void getEventBookingsWithStatusFiltersByStatus() {
        Event event = event(50, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        when(bookingRepository.findAllByEvent_IdAndStatusOrderByBookedOnDescIdDesc(
                EVENT_ID, BookingStatus.REJECTED))
                .thenReturn(List.of(rejectedBooking(event)));

        List<BookingResponse> responses =
                service.getEventBookings(EVENT_ID, BookingStatus.REJECTED);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).rejectionReason()).isEqualTo("Some reason");
        verify(bookingRepository, never())
                .findAllByEvent_IdOrderByBookedOnDescIdDesc(any());
    }

    // ------------------------------------------------------------------
    // getEventSeatMap
    // ------------------------------------------------------------------

    @Test
    void getEventSeatMapThrowsWhenEventMissing() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventSeatMap(EVENT_ID))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void getEventSeatMapSplitsBookedAndAvailableSeats() {
        Event event = event(6, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED))
                .thenReturn(List.of(
                        confirmedBooking(event, "4, 2", 2, "200.00"),
                        confirmedBooking(event, "1", 1, "100.00"),
                        confirmedBooking(event, null, 0, "0.00"),
                        confirmedBooking(event, "   ", 0, "0.00")));

        EventSeatMapResponse response = service.getEventSeatMap(EVENT_ID);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.totalSeats()).isEqualTo(6);
        assertThat(response.bookedSeats()).containsExactly(1, 2, 4);
        assertThat(response.availableSeats()).containsExactly(3, 5, 6);
    }

    @Test
    void getEventSeatMapWithNoBookingsHasEverySeatAvailable() {
        givenEvent(event(3, LocalDate.now().plusDays(60), "1000.00"));
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED)).thenReturn(List.of());

        EventSeatMapResponse response = service.getEventSeatMap(EVENT_ID);

        assertThat(response.bookedSeats()).isEmpty();
        assertThat(response.availableSeats()).containsExactly(1, 2, 3);
    }

    // ------------------------------------------------------------------
    // getEventSummary
    // ------------------------------------------------------------------

    @Test
    void getEventSummaryThrowsWhenEventMissing() {
        when(eventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getEventSummary(EVENT_ID))
                .isInstanceOf(EventNotFoundException.class);
    }

    @Test
    void getEventSummaryAggregatesSeatsRevenueAndRejections() {
        Event event = event(100, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED))
                .thenReturn(List.of(
                        confirmedBooking(event, "1,2", 2, "855.00"),
                        confirmedBooking(event, "3", 1, "100.50"),
                        // A confirmed booking without an amount must be ignored in revenue.
                        confirmedBooking(event, "4", 1, null)));
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.REJECTED))
                .thenReturn(List.of(rejectedBooking(event), rejectedBooking(event)));

        EventSummaryResponse response = service.getEventSummary(EVENT_ID);

        assertThat(response.eventId()).isEqualTo(EVENT_ID);
        assertThat(response.eventName()).isEqualTo("Rock Night");
        assertThat(response.totalSeats()).isEqualTo(100);
        assertThat(response.seatsSold()).isEqualTo(4);
        assertThat(response.seatsAvailable()).isEqualTo(96);
        assertThat(response.confirmedBookings()).isEqualTo(3);
        assertThat(response.rejectedBookings()).isEqualTo(2);
        assertThat(response.totalRevenue()).isEqualByComparingTo("955.50");
        assertThat(response.occupancyPct()).isEqualByComparingTo("4.0");
        assertThat(response.demandBand()).isEqualTo("LOW");
    }

    @ParameterizedTest
    @CsvSource({
            "0, LOW, 0.0",
            "39, LOW, 39.0",
            "40, MEDIUM, 40.0",
            "75, MEDIUM, 75.0",
            "76, HIGH, 76.0",
            "100, HIGH, 100.0"
    })
    void getEventSummaryClassifiesDemandBands(
            int seatsSold, String expectedBand, String expectedOccupancy) {

        Event event = event(100, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        List<Booking> confirmed = seatsSold == 0
                ? List.of()
                : List.of(confirmedBooking(event, "1", seatsSold, "100.00"));
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED)).thenReturn(confirmed);
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.REJECTED)).thenReturn(List.of());

        EventSummaryResponse response = service.getEventSummary(EVENT_ID);

        assertThat(response.demandBand()).isEqualTo(expectedBand);
        assertThat(response.occupancyPct()).isEqualByComparingTo(expectedOccupancy);
    }

    @Test
    void getEventSummaryRoundsOccupancyToOneDecimal() {
        Event event = event(3, LocalDate.now().plusDays(60), "1000.00");
        givenEvent(event);
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.CONFIRMED))
                .thenReturn(List.of(confirmedBooking(event, "1", 1, "100.00")));
        when(bookingRepository.findAllByEvent_IdAndStatus(
                EVENT_ID, BookingStatus.REJECTED)).thenReturn(List.of());

        EventSummaryResponse response = service.getEventSummary(EVENT_ID);

        assertThat(response.occupancyPct()).isEqualByComparingTo("33.3");
        assertThat(response.demandBand()).isEqualTo("LOW");
    }
}