package com.skala.rooms.auth;

public class AccountException extends RuntimeException {

    private final String code;

    public AccountException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
