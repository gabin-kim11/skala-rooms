package com.skala.rooms.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.bouncycastle.crypto.generators.SCrypt;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 기존 Next.js의 node:crypto scrypt 결과(saltHex:derivedHex)와 호환됩니다.
 */
public class NodeScryptPasswordEncoder implements PasswordEncoder {

    private static final int COST = 16_384;
    private static final int BLOCK_SIZE = 8;
    private static final int PARALLELIZATION = 1;
    private static final int KEY_LENGTH = 64;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String encode(CharSequence rawPassword) {
        byte[] saltBytes = new byte[16];
        RANDOM.nextBytes(saltBytes);
        String saltHex = HexFormat.of().formatHex(saltBytes);
        byte[] derived = derive(rawPassword, saltHex, KEY_LENGTH);
        return saltHex + ":" + HexFormat.of().formatHex(derived);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) return false;
        String[] parts = encodedPassword.split(":", 2);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) return false;
        try {
            byte[] expected = HexFormat.of().parseHex(parts[1]);
            byte[] actual = derive(rawPassword, parts[0], expected.length);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private byte[] derive(CharSequence rawPassword, String saltHexText, int length) {
        return SCrypt.generate(
            rawPassword.toString().getBytes(StandardCharsets.UTF_8),
            saltHexText.getBytes(StandardCharsets.UTF_8),
            COST,
            BLOCK_SIZE,
            PARALLELIZATION,
            length
        );
    }
}
