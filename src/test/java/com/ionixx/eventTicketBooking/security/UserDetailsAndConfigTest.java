package com.ionixx.eventticketbooking.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ionixx.eventticketbooking.entity.Role;
import com.ionixx.eventticketbooking.entity.User;
import com.ionixx.eventticketbooking.repository.UserRepository;

class UserDetailsAndConfigTest {

    private final UserRepository userRepository = mock(UserRepository.class);

    private static User user(int id, String email, Role role) {
        User user = new User("Test User", email, "stored-hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    // ------------------------------------------------------------------
    // CustomUserDetailsService
    // ------------------------------------------------------------------

    @Test
    void loadsUserByNormalisedEmail() {
        when(userRepository.findByEmail("cust@example.com"))
                .thenReturn(Optional.of(user(4, "cust@example.com", Role.CUSTOMER)));
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        UserDetails details = service.loadUserByUsername("  Cust@Example.COM ");

        assertThat(details.getUsername()).isEqualTo("cust@example.com");
        assertThat(details.getPassword()).isEqualTo("stored-hash");
        assertThat(details.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void unknownEmailThrowsUsernameNotFound() {
        when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername("nobody@example.com"))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessage("User not found");
    }

    @Test
    void nullEmailIsTreatedAsEmptyAndNotFound() {
        when(userRepository.findByEmail("")).thenReturn(Optional.empty());
        CustomUserDetailsService service = new CustomUserDetailsService(userRepository);

        assertThatThrownBy(() -> service.loadUserByUsername(null))
                .isInstanceOf(UsernameNotFoundException.class);
        verify(userRepository).findByEmail("");
    }

    // ------------------------------------------------------------------
    // UserPrincipal
    // ------------------------------------------------------------------

    @Test
    void principalIsBuiltFromUserEntity() {
        UserPrincipal principal =
                UserPrincipal.from(user(9, "org@example.com", Role.ORGANISER));

        assertThat(principal.id()).isEqualTo(9);
        assertThat(principal.email()).isEqualTo("org@example.com");
        assertThat(principal.role()).isEqualTo(Role.ORGANISER);
        assertThat(principal.getUsername()).isEqualTo("org@example.com");
        assertThat(principal.getPassword()).isEqualTo("stored-hash");
        assertThat(principal.getAuthorities())
                .extracting(Object::toString)
                .containsExactly("ROLE_ORGANISER");
    }

    @Test
    void principalAccountFlagsAreAllEnabled() {
        UserPrincipal principal =
                new UserPrincipal(1, "a@example.com", "hash", Role.CUSTOMER);

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }

    // ------------------------------------------------------------------
    // DemoUserSeeder
    // ------------------------------------------------------------------

    @Test
    void seederCreatesBothDemoUsersWhenMissing() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("encoded");
        when(userRepository.existsByEmail(any())).thenReturn(false);

        new DemoUserSeeder(userRepository, encoder).run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(2)).save(captor.capture());

        assertThat(captor.getAllValues())
                .extracting(User::getEmail)
                .containsExactly("organiser@example.com", "customer@example.com");
        assertThat(captor.getAllValues())
                .extracting(User::getRole)
                .containsExactly(Role.ORGANISER, Role.CUSTOMER);
        assertThat(captor.getAllValues())
                .extracting(User::getPasswordHash)
                .containsOnly("encoded");
        verify(encoder).encode("Organiser@123");
        verify(encoder).encode("Customer@123");
    }

    @Test
    void seederSkipsUsersThatAlreadyExist() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(userRepository.existsByEmail(any())).thenReturn(true);

        new DemoUserSeeder(userRepository, encoder).run(mock(ApplicationArguments.class));

        verify(userRepository, never()).save(any());
        verify(encoder, never()).encode(any());
    }

    @Test
    void seederCreatesOnlyTheMissingUser() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(any())).thenReturn("encoded");
        when(userRepository.existsByEmail("organiser@example.com")).thenReturn(true);
        when(userRepository.existsByEmail("customer@example.com")).thenReturn(false);

        new DemoUserSeeder(userRepository, encoder).run(mock(ApplicationArguments.class));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("customer@example.com");
    }

    // ------------------------------------------------------------------
    // SecurityConfig beans
    // ------------------------------------------------------------------

    @Test
    void passwordEncoderBeanIsBCrypt() {
        PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

        assertThat(encoder).isInstanceOf(BCryptPasswordEncoder.class);
        String hash = encoder.encode("Passw0rd!23");
        assertThat(hash).startsWith("$2").isNotEqualTo("Passw0rd!23");
        assertThat(encoder.matches("Passw0rd!23", hash)).isTrue();
        assertThat(encoder.matches("wrong", hash)).isFalse();
    }

    @Test
    void authenticationManagerBeanComesFromAuthenticationConfiguration() throws Exception {
        AuthenticationConfiguration configuration = mock(AuthenticationConfiguration.class);
        AuthenticationManager manager = mock(AuthenticationManager.class);
        when(configuration.getAuthenticationManager()).thenReturn(manager);

        assertThat(new SecurityConfig().authenticationManager(configuration)).isSameAs(manager);
    }

    // ------------------------------------------------------------------
    // Simple configuration classes
    // ------------------------------------------------------------------

    @Test
    void openApiConfigCanBeInstantiated() {
        assertThat(new OpenApiConfig()).isNotNull();
    }

    @Test
    void jwtPropertiesExposeConfiguredValues() {
        JwtProperties properties =
                new JwtProperties("a-secret", Duration.ofMinutes(30), "issuer");

        assertThat(properties.secret()).isEqualTo("a-secret");
        assertThat(properties.expiration()).isEqualTo(Duration.ofMinutes(30));
        assertThat(properties.issuer()).isEqualTo("issuer");
    }
}