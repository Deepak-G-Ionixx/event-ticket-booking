package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ionixx.eventticketbooking.entity.Role;

class CurrentUserTest {

    @AfterEach
    void clearContext() {
        SecurityTestSupport.clear();
    }

    @Test
    void requireReturnsTheLoggedInUser() {
        SecurityTestSupport.actAs(Role.ORGANISER, 12);

        UserPrincipal user = CurrentUser.require();

        assertThat(user.id()).isEqualTo(12);
        assertThat(user.role()).isEqualTo(Role.ORGANISER);
    }

    @Test
    void requireFailsWhenNobodyIsLoggedIn() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(CurrentUser::require)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void requireFailsForAnonymousPrincipal() {
        // Spring Security represents "not logged in" as a plain String principal.
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "anonymousUser", null, java.util.List.of()));
        SecurityContextHolder.setContext(context);

        assertThatThrownBy(CurrentUser::require)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
