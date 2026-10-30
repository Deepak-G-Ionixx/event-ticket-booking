
package com.ionixx.eventticketbooking.exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.ionixx.eventticketbooking.dto.ApiErrorResponse;
import com.ionixx.eventticketbooking.filter.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final HttpServletRequest httpServletRequest;

    public GlobalExceptionHandler(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    // Handles @Valid request DTO validation failures.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException exception) {

        List<ApiErrorResponse.FieldError> errors = new ArrayList<>();

        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.add(new ApiErrorResponse.FieldError(
                        error.getField(),
                        error.getRejectedValue(),
                        error.getDefaultMessage()
                ))
        );

        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.add(new ApiErrorResponse.FieldError(
                        error.getObjectName(),
                        null,
                        error.getDefaultMessage()
                ))
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "Request has " + errors.size() + " invalid field(s)",
                errors
        );
    }

    // Handles malformed JSON and invalid JSON values.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
            HttpMessageNotReadableException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST_BODY",
                "Request body is missing, malformed, or contains an invalid value",
                List.of()
        );
    }

    // Handles invalid event IDs.
    @ExceptionHandler(InvalidEventIdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidEventId(
            InvalidEventIdException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_EVENT_ID",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles duplicate events.
    @ExceptionHandler(DuplicateEventException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateEvent(
            DuplicateEventException exception) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "DUPLICATE_EVENT",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles deleting an event that already has bookings.
    @ExceptionHandler(EventHasBookingsException.class)
    public ResponseEntity<ApiErrorResponse> handleEventHasBookings(
            EventHasBookingsException exception) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "EVENT_HAS_BOOKINGS",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles events that do not exist.
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleEventNotFound(
            EventNotFoundException exception) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "EVENT_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles invalid booking IDs.
    @ExceptionHandler(InvalidBookingIdException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBookingId(
            InvalidBookingIdException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_BOOKING_ID",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles bookings that do not exist.
    @ExceptionHandler(BookingNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleBookingNotFound(
            BookingNotFoundException exception) {

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "BOOKING_NOT_FOUND",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles invalid booking requests, such as duplicate seat numbers
    // within the same request.
    @ExceptionHandler(InvalidBookingRequestException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidBookingRequest(
            InvalidBookingRequestException exception) {

        List<ApiErrorResponse.FieldError> errors = List.of(
                new ApiErrorResponse.FieldError(
                        exception.getField(),
                        null,
                        exception.getMessage()
                )
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_BOOKING_REQUEST",
                "Booking request is invalid",
                errors
        );
    }

    // Handles seat numbers greater than the event's total seat count.
    @ExceptionHandler(SeatOutOfRangeException.class)
    public ResponseEntity<ApiErrorResponse> handleSeatOutOfRange(
            SeatOutOfRangeException exception) {

        List<ApiErrorResponse.FieldError> errors = List.of(
                new ApiErrorResponse.FieldError(
                        "seat_numbers",
                        exception.getSeatNumber(),
                        exception.getMessage()
                )
        );

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "SEAT_OUT_OF_RANGE",
                exception.getMessage(),
                errors
        );
    }

    // Handles a seat already reserved by another confirmed booking.
    // PostgreSQL's unique constraint protects against concurrent requests.
    @ExceptionHandler(SeatAlreadyBookedException.class)
    public ResponseEntity<ApiErrorResponse> handleSeatAlreadyBooked(
            SeatAlreadyBookedException exception) {

        log.warn(
                "Seat reservation conflict: requestId={}, path={}",
                getRequestId(),
                httpServletRequest.getRequestURI()
        );

        return buildResponse(
                HttpStatus.CONFLICT,
                "SEAT_ALREADY_BOOKED",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles a query parameter of the wrong type, for example page=abc.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                "Parameter '" + exception.getName() + "' has an invalid value",
                List.of()
        );
    }

    // Preserves handling of other invalid arguments.
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleIllegalArgument(
            IllegalArgumentException exception) {

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "INVALID_REQUEST",
                exception.getMessage(),
                List.of()
        );
    }

    // Handles authentication failures raised inside controller processing.
    // Failures inside the security filter chain use RestAuthenticationEntryPoint.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception) {

        boolean badCredentials =
                exception instanceof BadCredentialsException;

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                badCredentials ? "INVALID_CREDENTIALS" : "UNAUTHORIZED",
                badCredentials
                        ? "Invalid email or password"
                        : "Authentication is required to access this resource",
                List.of(),
                "Bearer"
        );
    }

    /**
     * Handles attempts to register an email address that already exists.
     * Returns HTTP 409 Conflict.
     */
    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ResponseEntity<ApiErrorResponse> handleEmailAlreadyRegistered(
            EmailAlreadyRegisteredException exception) {

        return buildResponse(
                HttpStatus.CONFLICT,
                "EMAIL_ALREADY_REGISTERED",
                exception.getMessage(),
                List.of());
    }

    /**
     * Handles attempts to access a resource owned by another user.
     * Returns HTTP 403 Forbidden because the user is authenticated
     * but does not have permission to access this resource.
     */
    @ExceptionHandler(ResourceAccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleResourceAccessDenied(
            ResourceAccessDeniedException exception) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "RESOURCE_ACCESS_DENIED",
                "You are not allowed to access this resource",
                List.of());
    }

    // Handles authenticated users who are not permitted to perform an action.
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception) {

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to perform this action",
                List.of()
        );
    }

    // Logs unexpected errors internally without exposing implementation details.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception) {

        log.error(
                "Unexpected error while processing request: requestId={}",
                getRequestId(),
                exception
        );

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                List.of()
        );
    }

    // Builds the consistent API error response used throughout the application.
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldError> errors) {

        return buildResponse(status, code, message, errors, null);
    }

    // Optionally adds the WWW-Authenticate header for authentication failures.
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldError> errors,
            String wwwAuthenticate) {

        String requestId = getRequestId();

        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                httpServletRequest.getRequestURI(),
                requestId,
                errors
        );

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);

        // Include the same request ID in the response header and error body.
        if (requestId != null && !requestId.isBlank()) {
            builder.header(RequestIdFilter.HEADER, requestId);
        }

        if (wwwAuthenticate != null) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        }

        return builder.body(body);
    }

    // Gets the request ID from MDC, falling back to the servlet request attribute.
    private String getRequestId() {
        String requestId = MDC.get(RequestIdFilter.MDC_KEY);

        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }

        Object requestAttribute =
                httpServletRequest.getAttribute(RequestIdFilter.MDC_KEY);

        return requestAttribute == null
                ? null
                : requestAttribute.toString();
    }
}
