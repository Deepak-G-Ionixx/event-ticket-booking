package com.ionixx.eventticketbooking.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.UserRepository;

@Component
@ConditionalOnProperty(name = "app.security.seed-demo-users", havingValue = "true")
public class DemoUserSeeder implements ApplicationRunner {

    private static final Logger log =
            LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoUserSeeder(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed("Demo Organiser", "organiser@example.com", "Organiser@123", Role.ORGANISER);
        seed("Demo Customer", "customer@example.com", "Customer@123", Role.CUSTOMER);
    }

    private void seed(String name, String email, String rawPassword, Role role) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        userRepository.save(
                new User(name, email, passwordEncoder.encode(rawPassword), role));
        log.info("Seeded demo user: email={}, role={}", email, role);
    }
}