package com.ionixx.eventticketbooking.security;

import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;
import com.ionixx.eventticketbooking.repository.UserRepository;

/**
 * Loads the database account of the logged-in user.
 *
 * The JWT proves WHO is calling (signature, expiry and issuer are verified by
 * JwtService). Before anything is written in that user's name, this service
 * re-reads the account from the database and checks that it still exists and
 * still has the expected role. A user id sent in a request body is never used.
 */
@Service
public class AuthenticatedUserService {

    private final UserRepository userRepository;

    public AuthenticatedUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the logged-in user's account when it has the required role.
     *
     * @throws org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
     *         when nobody is logged in (HTTP 401)
     * @throws ResourceAccessDeniedException when the role does not match or the
     *         account no longer exists (HTTP 403)
     */
    public User requireCurrentUser(Role requiredRole) {
        return load(requiredRole, userRepository::findById);
    }

    /**
     * Same as {@link #requireCurrentUser(Role)}, but locks the user's row until
     * the surrounding transaction ends (SELECT ... FOR UPDATE).
     *
     * Two simultaneous bookings by the SAME customer are therefore processed
     * one after the other, so the "6 seats per customer per event" rule cannot
     * be bypassed by sending parallel requests. Different customers never wait
     * for each other.
     *
     * MANDATORY: the lock is useless without the caller's transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public User requireCurrentUserForUpdate(Role requiredRole) {
        return load(requiredRole, userRepository::findByIdForUpdate);
    }

    private User load(
            Role requiredRole,
            Function<Integer, Optional<User>> finder) {

        UserPrincipal principal = CurrentUser.require();

        // Role in the token must match before the database is even queried.
        if (principal.role() != requiredRole) {
            throw new ResourceAccessDeniedException();
        }

        User user = finder.apply(principal.id())
                .orElseThrow(ResourceAccessDeniedException::new);

        // The stored role is authoritative (the token could be older than a role change).
        if (user.getRole() != requiredRole) {
            throw new ResourceAccessDeniedException();
        }

        return user;
    }
}
