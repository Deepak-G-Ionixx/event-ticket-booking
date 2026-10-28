package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.dto.LoginRequest;
import com.ionixx.eventticketbooking.dto.LoginResponse;
import com.ionixx.eventticketbooking.dto.RegisterRequest;
import com.ionixx.eventticketbooking.dto.RegisterResponse;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.EmailAlreadyRegisteredException;
import com.ionixx.eventticketbooking.repository.UserRepository;
import com.ionixx.eventticketbooking.security.JwtAuthenticationFilter;
import com.ionixx.eventticketbooking.security.JwtService;
import com.ionixx.eventticketbooking.security.UserPrincipal;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

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
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    // ------------------------------------------------------------------
    // register
    // ------------------------------------------------------------------

    @Test
    void registerCreatesCustomerWithHashedPassword() {
        RegisterRequest request =
                new RegisterRequest("  John Doe ", " John@Example.COM ", "password123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 11);
            return user;
        });

        RegisterResponse response = authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("HASHED");
        assertThat(saved.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(saved.getEmail()).isEqualTo("john@example.com");
        assertThat(saved.getName()).isEqualTo("John Doe");

        assertThat(response.userId()).isEqualTo(11);
        assertThat(response.name()).isEqualTo("John Doe");
        assertThat(response.email()).isEqualTo("john@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(response.message()).isEqualTo("Customer account registered successfully");
    }

    @Test
    void registerFailsWhenEmailAlreadyExists() {
        RegisterRequest request =
                new RegisterRequest("John Doe", "john@example.com", "password123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("An account with this email address already exists");

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void registerTranslatesDatabaseUniqueViolationIntoDuplicateEmailError() {
        RegisterRequest request =
                new RegisterRequest("John Doe", "john@example.com", "password123");
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("HASHED");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyRegisteredException.class)
                .hasMessage("An account with this email address already exists");
    }

    // ------------------------------------------------------------------
    // login
    // ------------------------------------------------------------------

    @Test
    void loginReturnsBearerTokenForValidCredentials() {
        UserPrincipal principal =
                new UserPrincipal(5, "cust@example.com", "hash", Role.CUSTOMER);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenReturn(authentication);
        when(jwtService.generateToken(principal)).thenReturn("signed.jwt.token");
        when(jwtService.getExpiresInSeconds()).thenReturn(3600L);

        LoginResponse response = authService.login(
                new LoginRequest("Cust@Example.com", "secret-pass"));

        assertThat(response.accessToken()).isEqualTo("signed.jwt.token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.userId()).isEqualTo(5);
        assertThat(response.email()).isEqualTo("cust@example.com");
        assertThat(response.role()).isEqualTo("CUSTOMER");
        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID)).isEqualTo("5");

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("cust@example.com");
        assertThat(captor.getValue().getCredentials()).isEqualTo("secret-pass");
    }

    @Test
    void loginRethrowsAuthenticationFailures() {
        BadCredentialsException failure = new BadCredentialsException("bad");
        when(authenticationManager.authenticate(any(Authentication.class)))
                .thenThrow(failure);

        assertThatThrownBy(() -> authService.login(
                new LoginRequest("cust@example.com", "wrong-pass")))
                .isSameAs(failure);

        verify(jwtService, never()).generateToken(any());
        assertThat(MDC.get(JwtAuthenticationFilter.MDC_USER_ID)).isNull();
    }
}
