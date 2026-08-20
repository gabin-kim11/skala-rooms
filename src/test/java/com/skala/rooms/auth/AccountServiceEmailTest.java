package com.skala.rooms.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountServiceEmailTest {

    @ParameterizedTest
    @ValueSource(strings = {"user@example.com", "skala.user+test@company.co.kr"})
    void acceptsUsableEmailAddresses(String email) {
        assertThat(AccountService.isEmailValid(email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "missing-at.example.com", "user@localhost", "user name@example.com"})
    void rejectsInvalidEmailAddresses(String email) {
        assertThat(AccountService.isEmailValid(email)).isFalse();
    }
}
