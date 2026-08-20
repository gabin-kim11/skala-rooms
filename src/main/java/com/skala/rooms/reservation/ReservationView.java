package com.skala.rooms.reservation;

import java.time.OffsetDateTime;

public record ReservationView(
    long id,
    long roomId,
    String roomCode,
    String roomName,
    String memberCode,
    String memberName,
    OffsetDateTime startsAt,
    OffsetDateTime endsAt,
    String purpose,
    int peopleCount,
    String status,
    boolean beforePhoto,
    boolean afterPhoto
) {
}
