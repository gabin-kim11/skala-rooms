package com.skala.rooms.reservation;

public record ReservationSummary(long totalCount, long completedCount, long totalMinutes) {
    public static ReservationSummary empty() {
        return new ReservationSummary(0, 0, 0);
    }
}
