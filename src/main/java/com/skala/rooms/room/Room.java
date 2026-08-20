package com.skala.rooms.room;

public record Room(
    long id,
    String roomCode,
    String name,
    String floor,
    int capacity,
    String features
) {
}
