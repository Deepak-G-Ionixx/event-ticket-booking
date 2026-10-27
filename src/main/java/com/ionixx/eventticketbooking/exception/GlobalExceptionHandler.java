
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

import com.ionixx.eventticketbooking.dto.ApiErrorResponse;
import com.ionixx.eventticketbooking.filter.RequestIdFilter;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final HttpServletRequest httpServletRequest;

    public GlobalExceptionHandler(
            HttpServletRequest httpServletRequest) {
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

    // Existing event handlers.
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

    // Booking ID is not numeric or is less than or equal to zero.
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

    // The requested booking does not exist.
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

    // Business validation failures, such as duplicate seat numbers.
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

    // A seat number is larger than the event's total_seats.
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

    // Preserves the existing handling of other bad arguments.
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

    // 401: wrong email/password on POST /auth/login (or any AuthenticationException
    // thrown from a controller). Failures inside the security filter chain are handled
    // by RestAuthenticationEntryPoint instead.
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception) {

        boolean badCredentials = exception instanceof BadCredentialsException;

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

    // 403: authenticated, but not allowed. Without this handler, an AccessDeniedException
    // thrown from a controller (for example by a future @PreAuthorize) would be caught
    // by the catch-all below and turned into a 500.
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

    // Catch-all: logs the real exception but avoids exposing internals.
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

    // Builds the consistent API error response used throughout the app.
    private ResponseEntity<ApiErrorResponse> buildResponse(
            HttpStatus status,
            String code,
            String message,
            List<ApiErrorResponse.FieldError> errors) {

        return buildResponse(status, code, message, errors, null);
    }

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

        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status)
                .header(RequestIdFilter.HEADER, requestId);

        if (wwwAuthenticate != null) {
            builder.header(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
        }

        return builder.body(body);
    }

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