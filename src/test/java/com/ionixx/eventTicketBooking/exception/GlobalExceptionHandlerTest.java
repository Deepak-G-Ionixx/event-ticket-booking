package com.ionixx.eventticketbooking.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import com.ionixx.eventticketbooking.dto.ApiErrorResponse;
import com.ionixx.eventticketbooking.filter.RequestIdFilter;

class GlobalExceptionHandlerTest {

    private static final String REQUEST_ID = "REQ-test-1234";

    private MockHttpServletRequest request;
    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/api/test");
        handler = new GlobalExceptionHandler(request);
        MDC.put(RequestIdFilter.MDC_KEY, REQUEST_ID);
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @SuppressWarnings("unused")
    private void sampleHandlerMethod(String value) {
        // Only used to build a MethodParameter for MethodArgumentNotValidException.
    }

    private static void assertError(
            ResponseEntity<ApiErrorResponse> response,
            HttpStatus status,
            String code,
            String message) {

        assertThat(response.getStatusCode()).isEqualTo(status);
        ApiErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.code()).isEqualTo(code);
        assertThat(body.message()).isEqualTo(message);
        assertThat(body.path()).isEqualTo("/api/test");
        assertThat(body.requestId()).isEqualTo(REQUEST_ID);
        assertThat(body.timestamp()).isNotNull();
        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER))
                .isEqualTo(REQUEST_ID);
    }

    // ------------------------------------------------------------------
    // simple handlers
    // ------------------------------------------------------------------

    @Test
    void emailAlreadyRegisteredReturns409() {
        ResponseEntity<ApiErrorResponse> response = handler.handleEmailAlreadyRegistered(
                new EmailAlreadyRegisteredException("exists"));

        assertError(response, HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "exists");
        assertThat(response.getBody().errors()).isEmpty();
    }

    @Test
    void invalidEventIdReturns400() {
        assertError(handler.handleInvalidEventId(new InvalidEventIdException()),
                HttpStatus.BAD_REQUEST, "INVALID_EVENT_ID",
                "Event ID must be a positive integer");
    }

    @Test
    void duplicateEventReturns409() {
        assertError(handler.handleDuplicateEvent(new DuplicateEventException()),
                HttpStatus.CONFLICT, "DUPLICATE_EVENT",
                "An event with the same name and date already exists");
    }

    @Test
    void eventNotFoundReturns404() {
        assertError(handler.handleEventNotFound(new EventNotFoundException(9)),
                HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event not found with id: 9");
    }

    @Test
    void invalidBookingIdReturns400() {
        assertError(handler.handleInvalidBookingId(new InvalidBookingIdException()),
                HttpStatus.BAD_REQUEST, "INVALID_BOOKING_ID",
                "Booking ID must be a positive integer");
    }

    @Test
    void bookingNotFoundReturns404() {
        assertError(handler.handleBookingNotFound(new BookingNotFoundException(3)),
                HttpStatus.NOT_FOUND, "BOOKING_NOT_FOUND", "Booking not found with id: 3");
    }

    @Test
    void illegalArgumentReturns400() {
        assertError(handler.handleIllegalArgument(new IllegalArgumentException("nope")),
                HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "nope");
    }

    @Test
    void unreadableBodyReturns400() {
        ResponseEntity<ApiErrorResponse> response = handler.handleUnreadableBody(
                mock(HttpMessageNotReadableException.class));

        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY",
                "Request body is missing, malformed, or contains an invalid value");
    }

    @Test
    void accessDeniedReturns403() {
        assertError(handler.handleAccessDenied(new AccessDeniedException("denied")),
                HttpStatus.FORBIDDEN, "FORBIDDEN",
                "You do not have permission to perform this action");
    }

    @Test
    void unexpectedErrorReturns500WithoutLeakingDetails() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleUnexpected(new IllegalStateException("secret internals"));

        assertError(response, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred");
        assertThat(response.getBody().message()).doesNotContain("secret");
    }

    // ------------------------------------------------------------------
    // handlers that add field errors
    // ------------------------------------------------------------------

    @Test
    void invalidBookingRequestReturnsFieldError() {
        ResponseEntity<ApiErrorResponse> response = handler.handleInvalidBookingRequest(
                new InvalidBookingRequestException("seat_numbers", "Duplicate seat numbers"));

        assertError(response, HttpStatus.BAD_REQUEST, "INVALID_BOOKING_REQUEST",
                "Booking request is invalid");
        assertThat(response.getBody().errors()).hasSize(1);
        ApiErrorResponse.FieldError error = response.getBody().errors().get(0);
        assertThat(error.field()).isEqualTo("seat_numbers");
        assertThat(error.rejectedValue()).isNull();
        assertThat(error.message()).isEqualTo("Duplicate seat numbers");
    }

    @Test
    void seatOutOfRangeReturnsFieldErrorWithRejectedSeat() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleSeatOutOfRange(new SeatOutOfRangeException(99));

        assertError(response, HttpStatus.BAD_REQUEST, "SEAT_OUT_OF_RANGE",
                "seat 99 does not exist for this event");
        ApiErrorResponse.FieldError error = response.getBody().errors().get(0);
        assertThat(error.field()).isEqualTo("seat_numbers");
        assertThat(error.rejectedValue()).isEqualTo(99);
    }

    @Test
    void validationFailureListsFieldAndGlobalErrors() throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.addError(
                new FieldError("target", "name", "x", false, null, null, "name is too short"));
        bindingResult.addError(new ObjectError("target", "object-level problem"));

        Method method = GlobalExceptionHandlerTest.class
                .getDeclaredMethod("sampleHandlerMethod", String.class);
        MethodArgumentNotValidException exception = new MethodArgumentNotValidException(
                new MethodParameter(method, 0), bindingResult);

        ResponseEntity<ApiErrorResponse> response =
                handler.handleValidationErrors(exception);

        assertError(response, HttpStatus.BAD_REQUEST, "VALIDATION_FAILED",
                "Request has 2 invalid field(s)");
        assertThat(response.getBody().errors()).hasSize(2);

        ApiErrorResponse.FieldError fieldError = response.getBody().errors().get(0);
        assertThat(fieldError.field()).isEqualTo("name");
        assertThat(fieldError.rejectedValue()).isEqualTo("x");
        assertThat(fieldError.message()).isEqualTo("name is too short");

        ApiErrorResponse.FieldError globalError = response.getBody().errors().get(1);
        assertThat(globalError.field()).isEqualTo("target");
        assertThat(globalError.rejectedValue()).isNull();
        assertThat(globalError.message()).isEqualTo("object-level problem");
    }

    // ------------------------------------------------------------------
    // authentication
    // ------------------------------------------------------------------

    @Test
    void badCredentialsReturns401WithBearerChallenge() {
        ResponseEntity<ApiErrorResponse> response =
                handler.handleAuthentication(new BadCredentialsException("bad"));

        assertError(response, HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS",
                "Invalid email or password");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer");
    }

    @Test
    void otherAuthenticationErrorsReturnGenericUnauthorized() {
        ResponseEntity<ApiErrorResponse> response = handler.handleAuthentication(
                new InsufficientAuthenticationException("need login"));

        assertError(response, HttpStatus.UNAUTHORIZED, "UNAUTHORIZED",
                "Authentication is required to access this resource");
        assertThat(response.getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE))
                .isEqualTo("Bearer");
    }

    // ------------------------------------------------------------------
    // request id resolution
    // ------------------------------------------------------------------

    @Test
    void requestIdFallsBackToRequestAttributeWhenMdcIsBlank() {
        MDC.put(RequestIdFilter.MDC_KEY, "   ");
        request.setAttribute(RequestIdFilter.MDC_KEY, "ATTR-ID-0001");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleInvalidEventId(new InvalidEventIdException());

        assertThat(response.getBody().requestId()).isEqualTo("ATTR-ID-0001");
        assertThat(response.getHeaders().getFirst(RequestIdFilter.HEADER))
                .isEqualTo("ATTR-ID-0001");
    }

    @Test
    void requestIdFallsBackToRequestAttributeWhenMdcIsMissing() {
        MDC.clear();
        request.setAttribute(RequestIdFilter.MDC_KEY, "ATTR-ID-0002");

        ResponseEntity<ApiErrorResponse> response =
                handler.handleInvalidEventId(new InvalidEventIdException());

        assertThat(response.getBody().requestId()).isEqualTo("ATTR-ID-0002");
    }

    @Test
    void requestIdIsNullWhenNeitherMdcNorAttributeHasOne() {
        MDC.clear();

        ResponseEntity<ApiErrorResponse> response =
                handler.handleInvalidEventId(new InvalidEventIdException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().requestId()).isNull();
    }
}
