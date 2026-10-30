
package com.ionixx.eventticketbooking.exception;

/**
 * Thrown when a logged-in user asks for an event or booking
 * that they are not authorized to access.
 *
 * The GlobalExceptionHandler converts this exception into
 * an HTTP 403 Forbidden response.
 */
public class ResourceAccessDeniedException extends RuntimeException {

    public ResourceAccessDeniedException() {
        super("You are not allowed to access this resource");
    }
}
