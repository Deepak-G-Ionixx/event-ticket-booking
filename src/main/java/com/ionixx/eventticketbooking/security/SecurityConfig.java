package com.ionixx.eventticketbooking.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.ionixx.eventticketbooking.entity.Role;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtService jwtService,
            RestAuthenticationEntryPoint authenticationEntryPoint,
            RestAccessDeniedHandler accessDeniedHandler) throws Exception {

        http
                // This is a stateless REST API; it does not use cookie-based sessions.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Disable browser login forms and HTTP Basic authentication.
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)

                // 401 means unauthenticated; 403 means authenticated but forbidden.
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))

                .authorizeHttpRequests(auth -> auth

                        // -------------------------------------------------
                        // Public endpoints: no JWT is required.
                        // -------------------------------------------------

                        // Login must be accessible so users can obtain a JWT.
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()

                        // Permit Swagger UI and its supporting static resources.
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**"
                        ).permitAll()

                        // Permit OpenAPI JSON/YAML and Swagger configuration.
                        .requestMatchers(
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()

                        // Permit the default error endpoint.
                        .requestMatchers("/error").permitAll()

                        // Permit application health checks.
                        .requestMatchers(
                                "/actuator/health",
                                "/actuator/health/**"
                        ).permitAll()

                        // -------------------------------------------------
                        // Role-restricted endpoints.
                        // -------------------------------------------------

                        // Only an ORGANISER can create an event.
                        .requestMatchers(HttpMethod.POST, "/api/events")
                        .hasRole(Role.ORGANISER.name())

                        // Only a CUSTOMER can create a booking.
                        .requestMatchers(HttpMethod.POST, "/api/bookings")
                        .hasRole(Role.CUSTOMER.name())

                        // -------------------------------------------------
                        // All remaining endpoints require a valid JWT.
                        // -------------------------------------------------
                        .anyRequest().authenticated())

                // Run JWT authentication inside Spring Security's filter chain.
                // Do not add @Component to JwtAuthenticationFilter because this
                // configuration creates the filter instance directly.
                .addFilterBefore(
                        new JwtAuthenticationFilter(jwtService),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt hashes passwords before they are stored in PostgreSQL.
        return new BCryptPasswordEncoder();
    }

    // Builds the AuthenticationManager using the configured user service
    // and password encoder. AuthService uses it during login.
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) throws Exception {

        return configuration.getAuthenticationManager();
    }
}