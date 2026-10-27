package com.ionixx.eventticketbooking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;

import com.ionixx.eventticketbooking.dto.LoginRequest;
import com.ionixx.eventticketbooking.dto.LoginResponse;
import com.ionixx.eventticketbooking.security.JwtAuthenticationFilter;
import com.ionixx.eventticketbooking.security.JwtService;
import com.ionixx.eventticketbooking.security.UserPrincipal;

@Service
public class AuthService {

    private static final Logger log =
            LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {

        final Authentication authentication;

        try {
            // Looks the user up by email and checks the password with BCrypt.
            // An unknown email and a wrong password both give BadCredentialsException,
            // so the API does not reveal which emails are registered.
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            request.email(), request.password()));
        } catch (AuthenticationException exception) {
            log.warn("Login failed: reason={}",
                    exception.getClass().getSimpleName());
            throw exception;
        }

        UserPrincipal user = (UserPrincipal) authentication.getPrincipal();

        MDC.put(JwtAuthenticationFilter.MDC_USER_ID, String.valueOf(user.id()));

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