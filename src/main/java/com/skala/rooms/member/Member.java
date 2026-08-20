package com.skala.rooms.member;

public record Member(long id, String memberCode, String name, String passwordHash) {
}
