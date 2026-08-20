package com.skala.rooms.reservation;

import java.time.LocalDate;
import java.time.LocalTime;

public record BookingRequest(
    long roomId,
    LocalDate date,
    LocalTime startTime,
    LocalTime endTime,
    String purpose,
    int peopleCount
) {
}
