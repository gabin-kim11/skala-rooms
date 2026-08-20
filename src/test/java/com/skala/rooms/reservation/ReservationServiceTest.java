package com.skala.rooms.reservation;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.skala.rooms.SkalaRoomsApplication;
import com.skala.rooms.room.RoomRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReservationServiceTest {

    private final ReservationService service = new ReservationService(
        mock(ReservationRepository.class),
        mock(RoomRepository.class),
        mock(JdbcTemplate.class)
    );

    @Test
    void rejectsMoreThanThreeHoursInOneReservation() {
        BookingRequest request = new BookingRequest(
            1,
            LocalDate.now(SkalaRoomsApplication.SEOUL).plusDays(1),
            LocalTime.of(9, 0),
            LocalTime.of(12, 30),
            "프로젝트 회의",
            2
        );

        assertThatThrownBy(() -> service.book(1, request))
            .isInstanceOf(BookingException.class)
            .hasMessage("invalid-reservation");
    }

    @Test
    void rejectsTimesOutsideClassHours() {
        BookingRequest request = new BookingRequest(
            1,
            LocalDate.now(SkalaRoomsApplication.SEOUL).plusDays(1),
            LocalTime.of(8, 30),
            LocalTime.of(9, 30),
            "프로젝트 회의",
            2
        );

        assertThatThrownBy(() -> service.book(1, request))
            .isInstanceOf(BookingException.class)
            .hasMessage("hours");
    }

    @Test
    void rejectsBeforePhotoEarlierThanFiveMinutesBeforeStart() {
        ReservationView reservation = reservationAt(
            Instant.parse("2026-08-05T01:00:00Z"),
            Instant.parse("2026-08-05T02:00:00Z")
        );

        assertThatThrownBy(() -> ReservationService.validatePhotoTiming(
            reservation,
            "before",
            Instant.parse("2026-08-05T00:54:59Z")
        ))
            .isInstanceOf(BookingException.class)
            .hasMessage("photo-too-early");
    }

    @Test
    void allowsBeforePhotoExactlyFiveMinutesBeforeStart() {
        ReservationView reservation = reservationAt(
            Instant.parse("2026-08-05T01:00:00Z"),
            Instant.parse("2026-08-05T02:00:00Z")
        );

        assertThatCode(() -> ReservationService.validatePhotoTiming(
            reservation,
            "before",
            Instant.parse("2026-08-05T00:55:00Z")
        )).doesNotThrowAnyException();
    }

    @Test
    void rejectsAfterPhotoBeforeReservationEnds() {
        ReservationView reservation = reservationAt(
            Instant.parse("2026-08-05T01:00:00Z"),
            Instant.parse("2026-08-05T02:00:00Z")
        );

        assertThatThrownBy(() -> ReservationService.validatePhotoTiming(
            reservation,
            "after",
            Instant.parse("2026-08-05T01:59:59Z")
        ))
            .isInstanceOf(BookingException.class)
            .hasMessage("photo-after-too-early");
    }

    private ReservationView reservationAt(Instant startsAt, Instant endsAt) {
        return new ReservationView(
            1,
            1,
            "201",
            "201호 회의실",
            "U000",
            "테스트",
            OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC),
            "테스트 회의",
            2,
            "approved",
            false,
            false
        );
    }
}
