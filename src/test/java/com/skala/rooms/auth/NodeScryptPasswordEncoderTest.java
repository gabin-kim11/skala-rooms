package com.skala.rooms.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NodeScryptPasswordEncoderTest {

    private final NodeScryptPasswordEncoder encoder = new NodeScryptPasswordEncoder();

    @Test
    void verifiesPasswordCreatedByTheExistingNextJsApp() {
        String nodeHash = "00112233445566778899aabbccddeeff:"
            + "791f673e6bdb380d91f76ccc0ba3469bac2d04f5188bdb65b866482efe94cd71"
            + "da2b19e8c28a04bdf2abe11f21df801e72f4cf5caf1038591aa21d68b0857238";

        assertThat(encoder.matches("password123", nodeHash)).isTrue();
        assertThat(encoder.matches("wrong-password", nodeHash)).isFalse();
    }

    @Test
    void createsAHashThatCanBeVerifiedAgain() {
        String hash = encoder.encode("another-password");

        assertThat(hash).contains(":");
        assertThat(encoder.matches("another-password", hash)).isTrue();
    }
}
