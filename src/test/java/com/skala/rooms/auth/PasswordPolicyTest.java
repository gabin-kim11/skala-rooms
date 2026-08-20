package com.skala.rooms.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    @Test
    void acceptsEightOrMoreCharactersWithEnglishAndSpecialCharacter() {
        assertThat(PasswordPolicy.isValid("Skala!2026")).isTrue();
        assertThat(PasswordPolicy.isValid("가빈Skala!")).isTrue();
    }

    @Test
    void rejectsPasswordWithoutEnglishLetter() {
        assertThat(PasswordPolicy.isValid("1234567!")).isFalse();
    }

    @Test
    void rejectsPasswordWithoutSpecialCharacter() {
        assertThat(PasswordPolicy.isValid("Skala2026")).isFalse();
    }

    @Test
    void rejectsShortOrWhitespacePassword() {
        assertThat(PasswordPolicy.isValid("Skala!1")).isFalse();
        assertThat(PasswordPolicy.isValid("Skala !2026")).isFalse();
    }
}
