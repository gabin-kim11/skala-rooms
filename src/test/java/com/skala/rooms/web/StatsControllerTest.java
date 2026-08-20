package com.skala.rooms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skala.rooms.member.Member;
import com.skala.rooms.reservation.ReservationRepository;
import com.skala.rooms.reservation.ReservationView;
import java.security.Principal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class StatsControllerTest {

    @Test
    void loadsHistoryOnlyForTheSignedInMember() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        MemberContext memberContext = mock(MemberContext.class);
        Principal principal = mock(Principal.class);
        when(memberContext.require(principal)).thenReturn(new Member(39, "U039", "테스트", "hash"));
        when(reservations.findHistoryForMember(39)).thenReturn(List.of());
        StatsController controller = new StatsController(reservations, memberContext);

        assertThat(controller.stats(principal, new ConcurrentModel())).isEqualTo("stats");

        verify(reservations).findHistoryForMember(39);
    }

    @Test
    void calculatesOnlyTheCurrentMembersApprovedUsage() {
        Instant now = Instant.parse("2026-08-05T03:00:00Z");
        List<ReservationView> history = List.of(
            reservation(1, now.minusSeconds(7200), now.minusSeconds(3600), "approved", true),
            reservation(2, now.plusSeconds(3600), now.plusSeconds(7200), "approved", false),
            reservation(3, now.minusSeconds(7200), now.minusSeconds(3600), "cancelled", false)
        );

        StatsController.UsageTotals totals = StatsController.UsageTotals.from(history, now);

        assertThat(totals.reservations()).isEqualTo(2);
        assertThat(totals.completed()).isEqualTo(1);
        assertThat(totals.upcoming()).isEqualTo(1);
        assertThat(totals.minutes()).isEqualTo(60);
    }

    private ReservationView reservation(long id, Instant startsAt, Instant endsAt, String status, boolean afterPhoto) {
        return new ReservationView(
            id,
            1,
            "201",
            "201호 회의실",
            "U039",
            "테스트",
            OffsetDateTime.ofInstant(startsAt, ZoneOffset.UTC),
            OffsetDateTime.ofInstant(endsAt, ZoneOffset.UTC),
            "프로젝트 회의",
            2,
            status,
            false,
            afterPhoto
        );
    }
}
