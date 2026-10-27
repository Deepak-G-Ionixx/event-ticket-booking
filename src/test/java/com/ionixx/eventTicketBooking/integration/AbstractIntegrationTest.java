package com.ionixx.eventticketbooking.integration;

import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.PostgreSQLContainer;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class AbstractIntegrationTest {

    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    // Singleton container: started once and shared by every subclass, so Spring's cached
    // application context never points at a stopped database.
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("eventticketdb")
                    .withUsername("postgres")
                    .withPassword("postgres")
                    .withEnv("TZ", "Asia/Kolkata")
                    .withEnv("PGTZ", "Asia/Kolkata");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",
                POSTGRES::getDriverClassName);
    }

    protected static final String PASSWORD = "Passw0rd!23";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @LocalServerPort
    protected int port;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected record ApiResult(int status, HttpHeaders headers, JsonNode body) {
    }

    // ---- users and tokens -------------------------------------------------

    protected String createUser(Role role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@test.com";
        userRepository.save(
                new User("Test " + role.name(), email,
                        passwordEncoder.encode(PASSWORD), role));
        return email;
    }

    protected String loginAndGetToken(String email, String password) {
        ApiResult result = call(HttpMethod.POST, "/auth/login", null,
                Map.of("email", email, "password", password));

        if (result.status() != 200) {
            throw new AssertionError("Login failed with status " + result.status());
        }
        return result.body().path("accessToken").asText();
    }

    protected String tokenFor(Role role) {
        return loginAndGetToken(createUser(role), PASSWORD);
    }

    // ---- HTTP helpers -----------------------------------------------------

    protected ApiResult call(HttpMethod method, String path, String token, Object body) {

        RestClient.RequestBodySpec spec = RestClient
                .create("http://localhost:" + port)
                .method(method)
                .uri(path);

        if (token != null) {
            spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (body != null) {
            spec.contentType(MediaType.APPLICATION_JSON).body(body);
        }

        // The empty error handler stops RestClient from throwing on 4xx/5xx.
        ResponseEntity<String> response = spec.retrieve()
                .onStatus(HttpStatusCode::isError, (request, clientResponse) -> { })
                .toEntity(String.class);

        String text = response.getBody();
        JsonNode json = (text == null || text.isBlank())
                ? JSON.missingNode()
                : JSON.readTree(text);

        return new ApiResult(response.getStatusCode().value(), response.getHeaders(), json);
    }

    protected ApiResult get(String path, String token) {
        return call(HttpMethod.GET, path, token, null);
    }

    protected ApiResult post(String path, String token, Object body) {
        return call(HttpMethod.POST, path, token, body);
    }
}