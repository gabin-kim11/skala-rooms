package com.skala.rooms.auth;

public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static boolean isValid(String password) {
        if (password == null || password.length() < MIN_LENGTH) return false;

        boolean hasEnglishLetter = false;
        boolean hasSpecialCharacter = false;
        for (int index = 0; index < password.length(); index++) {
            char character = password.charAt(index);
            if (Character.isWhitespace(character)) return false;
            if ((character >= 'A' && character <= 'Z') || (character >= 'a' && character <= 'z')) {
                hasEnglishLetter = true;
            } else if (!Character.isLetterOrDigit(character)) {
                hasSpecialCharacter = true;
            }
        }
        return hasEnglishLetter && hasSpecialCharacter;
    }
}
