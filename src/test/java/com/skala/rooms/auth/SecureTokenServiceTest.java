package com.skala.rooms.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecureTokenServiceTest {

    private final SecureTokenService tokens = new SecureTokenService();

    @Test
    void createsUniqueUrlSafeTokensAndOnlyStoresStableHashes() {
        String first = tokens.create();
        String second = tokens.create();

        assertThat(first).hasSize(43).matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
        assertThat(tokens.hash(first)).hasSize(64).doesNotContain(first);
        assertThat(tokens.hash(first)).isEqualTo(tokens.hash(first));
    }
}
