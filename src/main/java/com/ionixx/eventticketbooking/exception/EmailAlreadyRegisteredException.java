
package com.ionixx.eventticketbooking.exception;

/**
 * Thrown when a registration request uses an email address
 * that already belongs to a registered user.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException(String message) {
        super(message);
    }
}
