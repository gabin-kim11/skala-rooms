package com.skala.rooms.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.skala.rooms.reservation.ReservationView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class HomeControllerTest {

    @Test
    void exposesCancellationOnlyForMyFutureReservation() {
        Instant now = Instant.parse("2026-08-05T01:00:00Z");
        ReservationView reservation = reservation("U039", now.plusSeconds(1800));

        HomeController.ScheduleItem mine = HomeController.ScheduleItem.from(reservation, "U039", now);
        HomeController.ScheduleItem anotherMember = HomeController.ScheduleItem.from(reservation, "U040", now);

        assertThat(mine.mine()).isTrue();
        assertThat(mine.cancellable()).isTrue();
        assertThat(mine.title()).isEqualTo("프로젝트 회의");
        assertThat(anotherMember.mine()).isFalse();
        assertThat(anotherMember.cancellable()).isFalse();
        assertThat(anotherMember.title()).isEqualTo("예약된 시간");
        assertThat(anotherMember.detail()).doesNotContain("U039", "테스트", "프로젝트 회의");
    }

    private ReservationView reservation(String memberCode, Instant startsAt) {
        return new ReservationView(
            1,
            1,
            "201",
            "201호 회의실",
            memberCode,
            "테스트",
            OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(startsAt.plusSeconds(1800), ZoneOffset.UTC),
            "프로젝트 회의",
            2,
            "approved",
            false,
            false
        );
    }
}
