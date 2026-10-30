package com.ionixx.eventticketbooking.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Gives services access to the logged-in user (set by JwtAuthenticationFilter).
 */
public final class CurrentUser {

    private CurrentUser() {
    }

    /**
     * Returns the logged-in user, or throws an AuthenticationException (HTTP 401)
     * when nobody is logged in.
     */
    public static UserPrincipal require() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException(
                    "Authentication is required to access this resource");
        }

        return principal;
    }
}
