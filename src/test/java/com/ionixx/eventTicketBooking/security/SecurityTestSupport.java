package com.ionixx.eventticketbooking.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ionixx.eventticketbooking.entity.Role;

/**
 * Test helper: "logs in" a user for code that reads the security context.
 * Always call clear() in an @AfterEach so the user does not leak into other tests.
 */
public final class SecurityTestSupport {

    private SecurityTestSupport() {
    }

    public static void actAs(Role role, int userId) {
        UserPrincipal principal = new UserPrincipal(
                userId, "user" + userId + "@example.com", null, role);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
