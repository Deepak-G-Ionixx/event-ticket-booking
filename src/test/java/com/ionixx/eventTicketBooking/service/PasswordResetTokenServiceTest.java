package com.ionixx.eventticketbooking.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordResetTokenServiceTest {

    private final PasswordResetTokenService service = new PasswordResetTokenService();

    @Test
    void generatedTokenIsUrlSafeBase64OfThirtyTwoBytes() {
        String token = service.generateToken();

        // 32 bytes -> 43 Base64 characters once the padding is removed.
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void generatedTokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 50; i++) {
            tokens.add(service.generateToken());
        }

        assertThat(tokens).hasSize(50);
    }

    @Test
    void hashIsTheKnownSha256HexOfTheToken() {
        // SHA-256("abc") from the FIPS 180-2 test vectors.
        assertThat(service.hashToken("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashIsSixtyFourCharactersAndDeterministic() {
        String token = service.generateToken();

        String first = service.hashToken(token);
        String second = service.hashToken(token);

        assertThat(first).hasSize(64).matches("[0-9a-f]+").isEqualTo(second);
        assertThat(first).isNotEqualTo(token);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankOrNullTokenCannotBeHashed(String token) {
        assertThatThrownBy(() -> service.hashToken(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Reset token must not be blank");
    }
}
