
package com.ionixx.eventticketbooking.security;

import com.ionixx.eventticketbooking.entity.Booking;
import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;

/**
 * Ownership rules: who may see which event or booking.
 *
 * Role checks (for example, "only an ORGANISER may create events")
 * live in SecurityConfig.
 *
 * These rules answer the next question: is this specific event or
 * booking accessible to the logged-in user?
 */
public final class AccessRules {

    private AccessRules() {
    }

    /**
     * Event details and seat map.
     * Customers may browse every event.
     * An organiser sees only their own events.
     */
    public static void requireCanViewEvent(
            UserPrincipal user,
            Event event) {

        if (user.role() == Role.CUSTOMER) {
            return;
        }

        requireEventOwner(user, event);
    }

    /**
     * Booking list and summary of an event:
     * only the organiser who created it.
     *
     * These responses contain other people's names, emails,
     * booking details and revenue.
     */
    public static void requireEventOwner(
            UserPrincipal user,
            Event event) {

        if (!ownsEvent(user, event)) {
            throw new ResourceAccessDeniedException();
        }
    }

    /**
     * A single booking can be viewed by:
     * 1. The customer who made the booking.
     * 2. The organiser who owns the associated event.
     */
    public static void requireCanViewBooking(
            UserPrincipal user,
            Booking booking) {

        boolean allowed = switch (user.role()) {
            case CUSTOMER -> booking.getCustomerId() != null
                    && booking.getCustomerId().equals(user.id());

            case ORGANISER -> ownsEvent(user, booking.getEvent());
        };

        if (!allowed) {
            throw new ResourceAccessDeniedException();
        }
    }

    /**
     * Checks whether the logged-in organiser owns this event.
     */
    private static boolean ownsEvent(
            UserPrincipal user,
            Event event) {

        return user.role() == Role.ORGANISER
                && event.getOrganiserId() != null
                && event.getOrganiserId().equals(user.id());
    }
}
