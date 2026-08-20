package com.skala.rooms.web;

import com.skala.rooms.member.Member;
import com.skala.rooms.reservation.ReservationRepository;
import com.skala.rooms.reservation.ReservationView;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StatsController {

    private final ReservationRepository reservations;
    private final MemberContext memberContext;

    public StatsController(ReservationRepository reservations, MemberContext memberContext) {
        this.reservations = reservations;
        this.memberContext = memberContext;
    }

    @GetMapping("/stats")
    public String stats(Principal principal, Model model) {
        Member member = memberContext.require(principal);
        Instant now = Instant.now();
        List<ReservationView> mine = reservations.findHistoryForMember(member.id());
        List<HistoryItem> history = mine.stream()
            .map(reservation -> HistoryItem.from(reservation, now))
            .toList();
        model.addAttribute("active", "stats");
        model.addAttribute("totals", UsageTotals.from(mine, now));
        model.addAttribute("history", history);
        return "stats";
    }

    public record UsageTotals(long reservations, long completed, long upcoming, long minutes) {
        static UsageTotals from(List<ReservationView> history, Instant now) {
            return new UsageTotals(
                history.stream().filter(UsageTotals::approved).count(),
                history.stream().filter(UsageTotals::approved).filter(ReservationView::afterPhoto).count(),
                history.stream().filter(UsageTotals::approved).filter(item -> item.startsAt().toInstant().isAfter(now)).count(),
                history.stream()
                    .filter(UsageTotals::approved)
                    .filter(item -> !item.endsAt().toInstant().isAfter(now))
                    .mapToLong(item -> Duration.between(item.startsAt(), item.endsAt()).toMinutes())
                    .sum()
            );
        }

        private static boolean approved(ReservationView reservation) {
            return reservation.status().equals("approved");
        }
    }

    public record HistoryItem(
        ReservationView reservation,
        String date,
        String range,
        String duration,
        String stateLabel,
        String stateClass,
        String proofLabel,
        boolean proofComplete
    ) {
        static HistoryItem from(ReservationView reservation, Instant now) {
            String stateLabel;
            String stateClass;
            if (reservation.status().equals("cancelled")) {
                stateLabel = "예약 취소";
                stateClass = "cancelled";
            } else if (reservation.startsAt().toInstant().isAfter(now)) {
                stateLabel = "예약 예정";
                stateClass = "upcoming";
            } else if (reservation.endsAt().toInstant().isAfter(now)) {
                stateLabel = "이용 중";
                stateClass = "active";
            } else {
                stateLabel = "이용 완료";
                stateClass = "completed";
            }
            boolean complete = reservation.afterPhoto();
            String proof = reservation.status().equals("cancelled") ? "인증 없음"
                : complete ? "이용 후 인증 완료"
                : reservation.endsAt().toInstant().isBefore(now) ? "이용 후 미인증" : "이용 예정";
            return new HistoryItem(
                reservation,
                ViewFormats.fullDate(reservation.startsAt()),
                ViewFormats.range(reservation.startsAt(), reservation.endsAt()),
                ViewFormats.duration(reservation.startsAt(), reservation.endsAt()),
                stateLabel,
                stateClass,
                proof,
                complete
            );
        }
    }
}
