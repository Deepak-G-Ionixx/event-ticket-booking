package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.BookingResponse;
import com.ionixx.eventticketbooking.dto.CreateBookingRequest;
import com.ionixx.eventticketbooking.dto.CreateEventRequest;
import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.BookingStatus;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;
import com.ionixx.eventticketbooking.repository.BookingRepository;
import com.ionixx.eventticketbooking.repository.BookingSeatRepository;
import com.ionixx.eventticketbooking.repository.EventRepository;
import com.ionixx.eventticketbooking.repository.UserRepository;
import com.ionixx.eventticketbooking.security.SecurityTestSupport;
import com.ionixx.eventticketbooking.security.AuthenticatedUserService;

/**
 * Tests who may create and access events and bookings.
 *
 * The real AuthenticatedUserService is used (only the repositories are
 * mocked), so these tests prove that ownership comes from the logged-in user
 * and the database account, never from values supplied in an API request.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ServiceOwnershipTest {

    private static final int EVENT_ID = 1;
    private static final int OWNER_ORGANISER = 10;
    private static final int OTHER_ORGANISER = 11;
    private static final int OWNER_CUSTOMER = 20;
    private static final int OTHER_CUSTOMER = 21;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingSeatRepository bookingSeatRepository;

    @Mock
    private UserRepository userRepository;

    private EventService eventService;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        AuthenticatedUserService authenticatedUserService =
                new AuthenticatedUserService(userRepository);

        eventService = new EventService(eventRepository, authenticatedUserService);

        bookingService = new BookingService(
                bookingRepository,
                eventRepository,
                bookingSeatRepository,
                authenticatedUserService);

        givenAccount(OWNER_ORGANISER, Role.ORGANISER);
        givenAccount(OTHER_ORGANISER, Role.ORGANISER);
        givenAccount(OWNER_CUSTOMER, Role.CUSTOMER);
        givenAccount(OTHER_CUSTOMER, Role.CUSTOMER);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityTestSupport.clear();
    }

    private void givenAccount(int id, Role role) {
        User user = new User(
                "User " + id, "user" + id + "@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);

        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(id)).thenReturn(Optional.of(user));
    }

    private static Event eventOwnedBy(Integer organiserId) {
        Event event = new Event(
                "Rock Night",
                "Chennai Hall",
                EventCategory.MUSIC,
                LocalDate.now().plusDays(60),
                50,
                new BigDecimal("1000.00"),
                organiserId);

        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        return event;
    }

    private static Booking bookingOf(Event event, Integer customerId) {
        Booking booking = new Booking(
                event,
                "Test User",
                "test@example.com",
                "1,2",
                2,
                BookingStatus.CONFIRMED,
                null,
                LocalDate.now(),
                customerId);

        booking.confirm(new BigDecimal("900.00"), new BigDecimal("1800.00"));
        ReflectionTestUtils.setField(booking, "id", 77);
        return booking;
    }

    private static CreateEventRequest eventRequest() {
        return new CreateEventRequest(
                "Java Conference",
                "Chennai Hall",
                EventCategory.TECH,
                LocalDate.now().plusDays(30),
                200,
                new BigDecimal("1499.00"));
    }

    // ---- Creating events --------------------------------------------------

    @Test
    void createEventStoresTheLoggedInOrganiserAsOwner() {
        SecurityTestSupport.actAs(Role.ORGANISER, OWNER_ORGANISER);

        when(eventRepository.saveAndFlush(any(Event.class)))
                .thenAnswer(invocation -> {
                    Event event = invocation.getArgument(0);
                    if (event.getId() == null) {
                        ReflectionTestUtils.setField(event, "id", 5);
                    }
                    return event;
                });

        eventService.createEvent(eventRequest());

        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        verify(eventRepository, atLeastOnce()).saveAndFlush(captor.capture());

        assertThat(captor.getAllValues().get(0).getOrganiserId())
                .isEqualTo(OWNER_ORGANISER);
        verify(userRepository).findById(OWNER_ORGANISER);
    }

    @Test
    void createEventFailsWhenNobodyIsLoggedIn() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> eventService.createEvent(eventRequest()))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void customerCannotCreateAnEvent() {
        SecurityTestSupport.actAs(Role.CUSTOMER, OWNER_CUSTOMER);

        assertThatThrownBy(() -> eventService.createEvent(eventRequest()))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    void organiserWhoseAccountWasDeletedCannotCreateAnEvent() {
        SecurityTestSupport.actAs(Role.ORGANISER, 999);
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.createEvent(eventRequest()))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    // ---- Creating bookings -----------------------------------------------

    @Test
    void createBookingStoresTheLoggedInCustomerAsOwnerEvenWithAnotherEmailInTheBody() {
        SecurityTestSupport.actAs(Role.CUSTOMER, OWNER_CUSTOMER);

        when(eventRepository.findByIdForShare(EVENT_ID))
                .thenReturn(Optional.of(eventOwnedBy(OWNER_ORGANISER)));
        when(bookingSeatRepository.findConfirmedSeatNumbersByEventId(EVENT_ID))
                .thenReturn(List.of());
        when(bookingRepository.countConfirmedSeatsForCustomerId(
                EVENT_ID, OWNER_CUSTOMER, BookingStatus.CONFIRMED))
                .thenReturn(0L);
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenAnswer(invocation -> {
                    Booking booking = invocation.getArgument(0);
                    ReflectionTestUtils.setField(booking, "id", 77);
                    return booking;
                });

        BookingResponse response = bookingService.createBooking(
                new CreateBookingRequest(
                        EVENT_ID,
                        "John Doe",
                        "user" + OTHER_CUSTOMER + "@example.com",
                        List.of(3, 4)));

        ArgumentCaptor<Booking> captor = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(captor.capture());

        assertThat(captor.getValue().getCustomerId()).isEqualTo(OWNER_CUSTOMER);
        assertThat(captor.getValue().getCustomerEmail())
                .isEqualTo("user" + OWNER_CUSTOMER + "@example.com");
        assertThat(response.status()).isEqualTo("CONFIRMED");

        // The customer row is locked to serialise this customer's bookings.
        verify(userRepository).findByIdForUpdate(OWNER_CUSTOMER);
        verify(bookingSeatRepository).saveAllAndFlush(any());
    }

    @Test
    void organiserCannotCreateABooking() {
        SecurityTestSupport.actAs(Role.ORGANISER, OWNER_ORGANISER);

        assertThatThrownBy(() -> bookingService.createBooking(
                new CreateBookingRequest(EVENT_ID, "John Doe", null, List.of(1))))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(bookingRepository, never()).saveAndFlush(any(Booking.class));
    }

    // ---- Event details and seat map --------------------------------------

    @Test
    void ownerOrganiserCanReadEvent() {
        SecurityTestSupport.actAs(Role.ORGANISER, OWNER_ORGANISER);
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(eventOwnedBy(OWNER_ORGANISER)));

        assertThat(eventService.getEventById(EVENT_ID).id()).isEqualTo(EVENT_ID);
    }

    @Test
    void otherOrganiserCannotReadEvent() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER);
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(eventOwnedBy(OWNER_ORGANISER)));

        assertThatThrownBy(() -> eventService.getEventById(EVENT_ID))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void customerCanReadAnyEventAndSeatMap() {
        SecurityTestSupport.actAs(Role.CUSTOMER, OWNER_CUSTOMER);
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(eventOwnedBy(OWNER_ORGANISER)));
        when(bookingSeatRepository.findConfirmedSeatNumbersByEventId(EVENT_ID))
                .thenReturn(List.of());

        assertThat(eventService.getEventById(EVENT_ID).id()).isEqualTo(EVENT_ID);
        assertThat(bookingService.getEventSeatMap(EVENT_ID).totalSeats()).isEqualTo(50);
    }

    @Test
    void otherOrganiserCannotReadSeatMap() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER);
        when(eventRepository.findById(EVENT_ID))
                .thenReturn(Optional.of(eventOwnedBy(OWNER_ORGANISER)));

        assertThatThrownBy(() -> bookingService.getEventSeatMap(EVENT_ID))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    // ---- Booking list and summary ----------------------------------------

    @Test
    void ownerQueryIsScopedToTheLoggedInOrganiser() {
        SecurityTestSupport.actAs(Role.ORGANISER, OWNER_ORGANISER);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, OWNER_ORGANISER))
                .thenReturn(Optional.of(eventOwnedBy(OWNER_ORGANISER)));
        when(bookingRepository.summarizeByStatus(EVENT_ID)).thenReturn(List.of());

        assertThat(bookingService.getEventSummary(EVENT_ID).eventId()).isEqualTo(EVENT_ID);

        // The owner id passed to the query is the logged-in organiser's id.
        verify(eventRepository).findByIdAndOrganiserId(EVENT_ID, OWNER_ORGANISER);
    }

    @Test
    void otherOrganiserCannotListBookingsOrReadSummary() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER);
        when(eventRepository.findByIdAndOrganiserId(EVENT_ID, OTHER_ORGANISER))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.getEventBookings(EVENT_ID, null, 0, 10, null))
                .isInstanceOf(ResourceAccessDeniedException.class);

        assertThatThrownBy(() -> bookingService.getEventSummary(EVENT_ID))
                .isInstanceOf(ResourceAccessDeniedException.class);

        // A user who does not own the event must not load its bookings.
        verify(bookingRepository, never()).findAllByEvent_Id(any(), any());
        verify(bookingRepository, never()).findAllByEvent_IdAndStatus(any(), any(), any());
        verify(bookingRepository, never()).summarizeByStatus(any());
    }

    @Test
    void customerCannotListBookingsOrReadSummary() {
        SecurityTestSupport.actAs(Role.CUSTOMER, OWNER_CUSTOMER);
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> bookingService.getEventBookings(EVENT_ID, null, 0, 10, null))
                .isInstanceOf(ResourceAccessDeniedException.class);

        assertThatThrownBy(() -> bookingService.getEventSummary(EVENT_ID))
                .isInstanceOf(ResourceAccessDeniedException.class);

        // A customer is never even looked up as an owner.
        verify(eventRepository, never()).findByIdAndOrganiserId(any(), any());
    }

    // ---- Updating and deleting events --------------------------------------

    @Test
    void otherOrganiserCannotUpdateOrDeleteAndNothingIsSaved() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER);
        when(eventRepository.findOwnedByIdForUpdate(EVENT_ID, OTHER_ORGANISER))
                .thenReturn(Optional.empty());
        when(eventRepository.existsById(EVENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> eventService.updateEvent(EVENT_ID, eventRequest()))
                .isInstanceOf(ResourceAccessDeniedException.class);
        assertThatThrownBy(() -> eventService.deleteEvent(EVENT_ID))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(eventRepository, never()).saveAndFlush(any(Event.class));
        verify(eventRepository, never()).delete(any(Event.class));
    }

    // ---- Single booking ---------------------------------------------------

    @Test
    void customerCanReadOwnBooking() {
        SecurityTestSupport.actAs(Role.CUSTOMER, OWNER_CUSTOMER);
        when(bookingRepository.findBookingWithEventById(77))
                .thenReturn(Optional.of(bookingOf(eventOwnedBy(OWNER_ORGANISER), OWNER_CUSTOMER)));

        assertThat(bookingService.getBookingById(77).id()).isEqualTo(77);
    }

    @Test
    void customerCannotReadAnotherCustomersBooking() {
        SecurityTestSupport.actAs(Role.CUSTOMER, OTHER_CUSTOMER);
        when(bookingRepository.findBookingWithEventById(77))
                .thenReturn(Optional.of(bookingOf(eventOwnedBy(OWNER_ORGANISER), OWNER_CUSTOMER)));

        assertThatThrownBy(() -> bookingService.getBookingById(77))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void organiserOfTheEventCanReadItsBookings() {
        SecurityTestSupport.actAs(Role.ORGANISER, OWNER_ORGANISER);
        when(bookingRepository.findBookingWithEventById(77))
                .thenReturn(Optional.of(bookingOf(eventOwnedBy(OWNER_ORGANISER), OWNER_CUSTOMER)));

        assertThat(bookingService.getBookingById(77).id()).isEqualTo(77);
    }

    @Test
    void otherOrganiserCannotReadBookingOfSomeoneElsesEvent() {
        SecurityTestSupport.actAs(Role.ORGANISER, OTHER_ORGANISER);
        when(bookingRepository.findBookingWithEventById(77))
                .thenReturn(Optional.of(bookingOf(eventOwnedBy(OWNER_ORGANISER), OWNER_CUSTOMER)));

        assertThatThrownBy(() -> bookingService.getBookingById(77))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }
}
