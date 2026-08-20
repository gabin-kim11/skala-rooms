package com.skala.rooms.reservation;

public class BookingException extends RuntimeException {

    private final String code;

    public BookingException(String code) {
        super(code);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
