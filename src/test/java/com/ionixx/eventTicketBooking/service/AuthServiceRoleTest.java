package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.RegisterRequest;
import com.ionixx.eventticketbooking.dto.RegisterResponse;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.UserRepository;
import com.ionixx.eventticketbooking.security.JwtService;

/**
 * Registration uses the role chosen in the request.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRoleTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager, jwtService, userRepository, passwordEncoder);

        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 31);
            return user;
        });
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        return captor.getValue();
    }

    @Test
    void organiserRoleCreatesAnOrganiserAccount() {
        RegisterResponse response = authService.register(new RegisterRequest(
                "Priya Kumar", "priya@example.com", "password123", Role.ORGANISER));

        assertThat(savedUser().getRole()).isEqualTo(Role.ORGANISER);
        assertThat(response.role()).isEqualTo("ORGANISER");
        assertThat(response.message()).isEqualTo("Organiser account registered successfully");
    }

    @Test
    void customerRoleCreatesACustomerAccount() {
        RegisterResponse response = authService.register(new RegisterRequest(
                "Priya Kumar", "priya@example.com", "password123", Role.CUSTOMER));

        assertThat(savedUser().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(response.message()).isEqualTo("Customer account registered successfully");
    }

    @Test
    void missingRoleCreatesACustomerAccount() {
        RegisterResponse response = authService.register(new RegisterRequest(
                "Priya Kumar", "priya@example.com", "password123", null));

        assertThat(savedUser().getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(response.role()).isEqualTo("CUSTOMER");
    }
}
