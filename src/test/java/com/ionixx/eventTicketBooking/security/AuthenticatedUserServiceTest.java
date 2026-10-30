package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.ResourceAccessDeniedException;
import com.ionixx.eventticketbooking.repository.UserRepository;

class AuthenticatedUserServiceTest {

    private static final int USER_ID = 42;

    private UserRepository userRepository;
    private AuthenticatedUserService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new AuthenticatedUserService(userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityTestSupport.clear();
    }

    private static User account(Role role) {
        User user = new User("Account", "account@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }

    @Test
    void returnsTheAccountOfTheLoggedInUser() {
        SecurityTestSupport.actAs(Role.ORGANISER, USER_ID);
        User organiser = account(Role.ORGANISER);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(organiser));

        assertThat(service.requireCurrentUser(Role.ORGANISER)).isSameAs(organiser);
        verify(userRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void lockingVariantUsesTheForUpdateQuery() {
        SecurityTestSupport.actAs(Role.CUSTOMER, USER_ID);
        User customer = account(Role.CUSTOMER);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(customer));

        assertThat(service.requireCurrentUserForUpdate(Role.CUSTOMER)).isSameAs(customer);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void wrongRoleInTheTokenIsDeniedWithoutQueryingTheDatabase() {
        SecurityTestSupport.actAs(Role.CUSTOMER, USER_ID);

        assertThatThrownBy(() -> service.requireCurrentUser(Role.ORGANISER))
                .isInstanceOf(ResourceAccessDeniedException.class);

        verify(userRepository, never()).findById(any());
    }

    @Test
    void deletedAccountIsDenied() {
        SecurityTestSupport.actAs(Role.CUSTOMER, USER_ID);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireCurrentUserForUpdate(Role.CUSTOMER))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void storedRoleWinsOverAnOlderTokenRole() {
        // The token still says ORGANISER, but the account is now a CUSTOMER.
        SecurityTestSupport.actAs(Role.ORGANISER, USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(account(Role.CUSTOMER)));

        assertThatThrownBy(() -> service.requireCurrentUser(Role.ORGANISER))
                .isInstanceOf(ResourceAccessDeniedException.class);
    }

    @Test
    void anonymousCallerGetsAnAuthenticationError() {
        SecurityTestSupport.clear();

        assertThatThrownBy(() -> service.requireCurrentUser(Role.CUSTOMER))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }
}
