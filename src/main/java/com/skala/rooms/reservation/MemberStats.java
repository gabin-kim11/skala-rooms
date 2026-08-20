package com.skala.rooms.reservation;

public record MemberStats(
    String memberCode,
    String name,
    long totalCount,
    long completedCount,
    long upcomingCount,
    long totalMinutes
) {
}
