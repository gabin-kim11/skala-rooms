package com.skala.rooms.web;

import com.skala.rooms.SkalaRoomsApplication;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ViewFormats {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN);
    private static final DateTimeFormatter FULL_DATE = DateTimeFormatter.ofPattern("yyyy년 M월 d일 (E)", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private ViewFormats() {
    }

    public static String date(OffsetDateTime value) {
        return value.atZoneSameInstant(SkalaRoomsApplication.SEOUL).format(DATE);
    }

    public static String fullDate(OffsetDateTime value) {
        return value.atZoneSameInstant(SkalaRoomsApplication.SEOUL).format(FULL_DATE);
    }

    public static String time(OffsetDateTime value) {
        return value.atZoneSameInstant(SkalaRoomsApplication.SEOUL).format(TIME);
    }

    public static String range(OffsetDateTime start, OffsetDateTime end) {
        return time(start) + "~" + time(end);
    }

    public static String duration(OffsetDateTime start, OffsetDateTime end) {
        long minutes = Duration.between(start, end).toMinutes();
        long hours = minutes / 60;
        long rest = minutes % 60;
        return (hours > 0 ? hours + "시간" : "") + (rest > 0 ? " " + rest + "분" : "");
    }
}
