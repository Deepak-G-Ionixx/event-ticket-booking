package com.ionixx.eventticketbooking.service;

import java.util.Optional;
import java.util.function.Function;

import com.ionixx.eventticketbooking.entity.Event;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.exception.EventNotFoundException;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;
import com.ionixx.eventticketbooking.repository.EventRepository;
import com.ionixx.eventticketbooking.security.UserPrincipal;

/**
 * Loads an event through an owner-scoped query
 * ({@code WHERE id = :id AND organiser_id = :currentUser}).
 *
 * Because the owner is part of the query, forgetting a separate ownership
 * check later cannot leak another organiser's event.
 *
 * Policy for inaccessible resources:
 * the event does not exist -> 404 EVENT_NOT_FOUND,
 * the event exists but belongs to someone else -> 403 RESOURCE_ACCESS_DENIED.
 */
final class OwnedEvents {

    private OwnedEvents() {
    }

    /**
     * @param ownedLookup receives the current organiser's id and runs the
     *                    owner-scoped repository query (with or without a lock)
     */
    static Event require(
            UserPrincipal user,
            Integer eventId,
            Function<Integer, Optional<Event>> ownedLookup,
            EventRepository eventRepository) {

        if (user.role() == Role.ORGANISER) {
            Optional<Event> owned = ownedLookup.apply(user.id());

            if (owned.isPresent()) {
                return owned.get();
            }
        }

        if (eventRepository.existsById(eventId)) {
            throw new ResourceAccessDeniedException();
        }

        throw new EventNotFoundException(eventId);
    }
}
