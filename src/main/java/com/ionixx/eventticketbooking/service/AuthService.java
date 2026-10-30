package com.ionixx.eventticketbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ionixx.eventticketbooking.dto.LoginRequest;
import com.ionixx.eventticketbooking.dto.LoginResponse;
import com.ionixx.eventticketbooking.dto.RegisterRequest;
import com.ionixx.eventticketbooking.dto.RegisterResponse;
import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.exception.EmailAlreadyRegisteredException;
import com.ionixx.eventticketbooking.security.JwtAuthenticationFilter;
import com.ionixx.eventticketbooking.security.JwtService;
import com.ionixx.eventticketbooking.security.UserPrincipal;
import com.ionixx.eventticketbooking.repository.UserRepository;

@Service
public class AuthService {

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Registers a new user as a CUSTOMER or an ORGANISER, as chosen in the request.
     *
     * The Role enum has no other values, so a registration request can never
     * create any other kind of account.
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {

        String normalizedEmail = request.email();

        // Check first so the API can return a meaningful duplicate-email error.
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException(
                    "An account with this email address already exists");
        }

        // Never store the raw password in the database.
        String passwordHash = passwordEncoder.encode(request.password());

        // The role comes from the request (CUSTOMER when it was left out).
        Role role = request.role();

        User newUser = new User(
                request.name(),
                normalizedEmail,
                passwordHash,
                role);

        try {
            // Flush here so a database unique-email constraint violation
            // can also be translated into a duplicate-email exception.
            User savedUser = userRepository.saveAndFlush(newUser);

            log.info("User registered successfully: userId={}, role={}",
                    savedUser.getId(), savedUser.getRole());

            return new RegisterResponse(
                    savedUser.getId(),
                    savedUser.getName(),
                    savedUser.getEmail(),
                    savedUser.getRole().name(),
                    role == Role.ORGANISER
                            ? "Organiser account registered successfully"
                            : "Customer account registered successfully");

        } catch (DataIntegrityViolationException exception) {
            // The database constraint protects against simultaneous
            // requests that attempt to register the same email.
            throw new EmailAlreadyRegisteredException(
                    "An account with this email address already exists");
        }
    }

    /**
     * Authenticates a user and returns a signed JWT access token.
     */
    public LoginResponse login(LoginRequest request) {

        final Authentication authentication;

        try {
            // Looks the user up by email and checks the password with BCrypt.
            // An unknown email and a wrong password both give a
            // BadCredentialsException, so the API does not reveal which
            // emails are registered.
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email(), request.password()));

        } catch (AuthenticationException exception) {
            log.warn("Login failed: reason={}",
                    exception.getClass().getSimpleName());
            throw exception;
        }

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();

        MDC.put(
                JwtAuthenticationFilter.MDC_USER_ID,
                String.valueOf(user.id()));

        String token = jwtService.generateToken(user);

        log.info("User logged in successfully: userId={}, role={}",
                user.id(), user.role());

        return new LoginResponse(
                token,
                "Bearer",
                jwtService.getExpiresInSeconds(),
                user.id(),
                user.email(),
                user.role().name());
    }
}