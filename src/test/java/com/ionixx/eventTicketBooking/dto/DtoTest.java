
package com.ionixx.eventticketbooking.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ionixx.eventticketbooking.entity.EventCategory;
import com.ionixx.eventticketbooking.entity.Role;

class DtoTest {

    // ------------------------------------------------------------------
    // CreateBookingRequest
    // ------------------------------------------------------------------

    @Test
    void createBookingRequestTrimsNameAndNormalisesEmail() {
        CreateBookingRequest request = new CreateBookingRequest(
                1, "  John Doe  ", "  John@Example.COM ", List.of(1, 2));

        assertThat(request.eventId()).isEqualTo(1);
        assertThat(request.customerName()).isEqualTo("John Doe");
        assertThat(request.customerEmail()).isEqualTo("john@example.com");
        assertThat(request.seatNumbers()).containsExactly(1, 2);
    }

    @Test
    void createBookingRequestKeepsNullFieldsNull() {
        CreateBookingRequest request = new CreateBookingRequest(null, null, null, null);

        assertThat(request.customerName()).isNull();
        assertThat(request.customerEmail()).isNull();
        assertThat(request.seatNumbers()).isNull();
    }

    @Test
    void createBookingRequestTakesAnImmutableCopyOfSeatNumbers() {
        List<Integer> original = new ArrayList<>(List.of(3, 4));
        CreateBookingRequest request =
                new CreateBookingRequest(1, "John Doe", "j@example.com", original);

        original.add(5);

        assertThat(request.seatNumbers()).containsExactly(3, 4);
        assertThatThrownBy(() -> request.seatNumbers().add(9))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // ------------------------------------------------------------------
    // CreateEventRequest
    // ------------------------------------------------------------------

    @Test
    void createEventRequestTrimsNameAndVenue() {
        CreateEventRequest request = new CreateEventRequest(
                "  Jazz Night ", "  Marina Hall  ", EventCategory.MUSIC,
                LocalDate.now().plusDays(5), 100, new BigDecimal("10.00"));

        assertThat(request.name()).isEqualTo("Jazz Night");
        assertThat(request.venue()).isEqualTo("Marina Hall");
        assertThat(request.category()).isEqualTo(EventCategory.MUSIC);
        assertThat(request.totalSeats()).isEqualTo(100);
        assertThat(request.basePrice()).isEqualByComparingTo("10.00");
        assertThat(request.eventDate()).isAfter(LocalDate.now());
    }

    @Test
    void createEventRequestKeepsNullNameAndVenueNull() {
        CreateEventRequest request =
                new CreateEventRequest(null, null, null, null, null, null);

        assertThat(request.name()).isNull();
        assertThat(request.venue()).isNull();
    }

    // ------------------------------------------------------------------
    // Auth request records
    // ------------------------------------------------------------------

    @Test
    void registerRequestNormalisesAndMasksPassword() {
        RegisterRequest request =
                new RegisterRequest(
                        "  John Doe ", " JOHN@Example.com ", "supersecret", Role.ORGANISER);

        assertThat(request.name()).isEqualTo("John Doe");
        assertThat(request.email()).isEqualTo("john@example.com");
        assertThat(request.password()).isEqualTo("supersecret");
        assertThat(request.role()).isEqualTo(Role.ORGANISER);
        assertThat(request).hasToString(
                "RegisterRequest[name=John Doe, email=john@example.com, "
                        + "password=***, role=ORGANISER]");
        assertThat(request.toString()).doesNotContain("supersecret");
    }

    @Test
    void registerRequestAllowsNullValues() {
        RegisterRequest request = new RegisterRequest(null, null, null, null);

        assertThat(request.name()).isNull();
        assertThat(request.email()).isNull();
        assertThat(request.password()).isNull();
        // No role given means a normal customer account.
        assertThat(request.role()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void loginRequestNormalisesEmailAndMasksPassword() {
        LoginRequest request = new LoginRequest("  USER@Example.com ", "supersecret");

        assertThat(request.email()).isEqualTo("user@example.com");
        assertThat(request.password()).isEqualTo("supersecret");
        assertThat(request).hasToString("LoginRequest[email=user@example.com, password=***]");
    }

    @Test
    void loginRequestAllowsNullEmail() {
        assertThat(new LoginRequest(null, "x").email()).isNull();
    }

    @Test
    void forgotPasswordRequestNormalisesEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest("  USER@Example.com ");

        assertThat(request.email()).isEqualTo("user@example.com");
        assertThat(request).hasToString("ForgotPasswordRequest[email=user@example.com]");
    }

    @Test
    void forgotPasswordRequestAllowsNullEmail() {
        assertThat(new ForgotPasswordRequest(null).email()).isNull();
    }

    @Test
    void resetPasswordRequestMasksSecretsInToString() {
        ResetPasswordRequest request = new ResetPasswordRequest("raw-token", "NewPassw0rd!");

        assertThat(request.token()).isEqualTo("raw-token");
        assertThat(request.newPassword()).isEqualTo("NewPassw0rd!");
        assertThat(request).hasToString("ResetPasswordRequest[token=***, newPassword=***]");
    }

    // ------------------------------------------------------------------
    // Response records
    // ------------------------------------------------------------------

    @Test
    void apiErrorResponseExposesAllFields() {
        Instant now = Instant.now();
        ApiErrorResponse.FieldError fieldError =
                new ApiErrorResponse.FieldError("name", "x", "too short");

        ApiErrorResponse response = new ApiErrorResponse(
                now, 400, "VALIDATION_FAILED", "bad", "/api/x", "REQ-1", List.of(fieldError));

        assertThat(response.timestamp()).isEqualTo(now);
        assertThat(response.status()).isEqualTo(400);
        assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
        assertThat(response.message()).isEqualTo("bad");
        assertThat(response.path()).isEqualTo("/api/x");
        assertThat(response.requestId()).isEqualTo("REQ-1");
        assertThat(response.errors()).containsExactly(fieldError);
        assertThat(fieldError.field()).isEqualTo("name");
        assertThat(fieldError.rejectedValue()).isEqualTo("x");
        assertThat(fieldError.message()).isEqualTo("too short");
    }

    @Test
    void bookingResponseExposesAllFields() {
        LocalDate today = LocalDate.now();
        BookingResponse response = new BookingResponse(
                1, 2, "John Doe", "john@example.com", List.of(4, 5), 2,
                new BigDecimal("10.00"), new BigDecimal("20.00"),
                "CONFIRMED", null, today);

        assertThat(response.id()).isEqualTo(1);
        assertThat(response.eventId()).isEqualTo(2);
        assertThat(response.customerName()).isEqualTo("John Doe");
        assertThat(response.customerEmail()).isEqualTo("john@example.com");
        assertThat(response.seatNumbers()).containsExactly(4, 5);
        assertThat(response.seats()).isEqualTo(2);
        assertThat(response.pricePerSeat()).isEqualByComparingTo("10.00");
        assertThat(response.totalAmount()).isEqualByComparingTo("20.00");
        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.rejectionReason()).isNull();
        assertThat(response.bookedOn()).isEqualTo(today);
    }

    @Test
    void eventResponseExposesAllFields() {
        LocalDate date = LocalDate.now().plusDays(3);
        EventResponse response = new EventResponse(
                1, "ETB0001", "Show", "Hall", EventCategory.THEATRE,
                date, 50, new BigDecimal("99.00"));

        assertThat(response.id()).isEqualTo(1);
        assertThat(response.eventLogId()).isEqualTo("ETB0001");
        assertThat(response.name()).isEqualTo("Show");
        assertThat(response.venue()).isEqualTo("Hall");
        assertThat(response.category()).isEqualTo(EventCategory.THEATRE);
        assertThat(response.eventDate()).isEqualTo(date);
        assertThat(response.totalSeats()).isEqualTo(50);
        assertThat(response.basePrice()).isEqualByComparingTo("99.00");
    }

    @Test
    void seatMapAndSummaryResponsesExposeAllFields() {
        EventSeatMapResponse seatMap =
                new EventSeatMapResponse(1, 10, List.of(1), List.of(2, 3));
        assertThat(seatMap.eventId()).isEqualTo(1);
        assertThat(seatMap.totalSeats()).isEqualTo(10);
        assertThat(seatMap.bookedSeats()).containsExactly(1);
        assertThat(seatMap.availableSeats()).containsExactly(2, 3);

        EventSummaryResponse summary = new EventSummaryResponse(
                1, "Show", 10, 4, 6, 2, 1,
                new BigDecimal("400.00"), new BigDecimal("40.0"), "MEDIUM");
        assertThat(summary.eventId()).isEqualTo(1);
        assertThat(summary.eventName()).isEqualTo("Show");
        assertThat(summary.totalSeats()).isEqualTo(10);
        assertThat(summary.seatsSold()).isEqualTo(4);
        assertThat(summary.seatsAvailable()).isEqualTo(6);
        assertThat(summary.confirmedBookings()).isEqualTo(2);
        assertThat(summary.rejectedBookings()).isEqualTo(1);
        assertThat(summary.totalRevenue()).isEqualByComparingTo("400.00");
        assertThat(summary.occupancyPct()).isEqualByComparingTo("40.0");
        assertThat(summary.demandBand()).isEqualTo("MEDIUM");
    }

    @Test
    void loginAndRegisterResponsesExposeAllFields() {
        LoginResponse login = new LoginResponse("jwt", "Bearer", 60L, 1, "a@example.com", "CUSTOMER");
        assertThat(login.accessToken()).isEqualTo("jwt");
        assertThat(login.tokenType()).isEqualTo("Bearer");
        assertThat(login.expiresIn()).isEqualTo(60L);
        assertThat(login.userId()).isEqualTo(1);
        assertThat(login.email()).isEqualTo("a@example.com");
        assertThat(login.role()).isEqualTo("CUSTOMER");

        RegisterResponse register =
                new RegisterResponse(2, "Jane Roe", "j@example.com", "CUSTOMER", "ok");
        assertThat(register.userId()).isEqualTo(2);
        assertThat(register.name()).isEqualTo("Jane Roe");
        assertThat(register.email()).isEqualTo("j@example.com");
        assertThat(register.role()).isEqualTo("CUSTOMER");
        assertThat(register.message()).isEqualTo("ok");
    }
}
