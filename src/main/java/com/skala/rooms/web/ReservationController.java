package com.skala.rooms.web;

import com.skala.rooms.member.Member;
import com.skala.rooms.reservation.BookingException;
import com.skala.rooms.reservation.BookingRequest;
import com.skala.rooms.reservation.PhotoData;
import com.skala.rooms.reservation.ReservationRepository;
import com.skala.rooms.reservation.ReservationService;
import com.skala.rooms.reservation.ReservationView;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class ReservationController {

    private final MemberContext memberContext;
    private final ReservationRepository reservations;
    private final ReservationService reservationService;

    public ReservationController(MemberContext memberContext, ReservationRepository reservations, ReservationService reservationService) {
        this.memberContext = memberContext;
        this.reservations = reservations;
        this.reservationService = reservationService;
    }

    @GetMapping("/reservations")
    public String reservations(Principal principal, Model model) {
        Member member = memberContext.require(principal);
        Instant now = Instant.now();
        List<ReservationCard> cards = reservations.findMine(member.id()).stream()
            .map(reservation -> ReservationCard.from(reservation, now))
            .toList();
        model.addAttribute("active", "reservations");
        model.addAttribute("cards", cards);
        model.addAttribute("upcomingCount", cards.stream().filter(card -> card.stateClass().equals("upcoming")).count());
        model.addAttribute("activeCount", cards.stream().filter(ReservationCard::activeReservation).count());
        model.addAttribute("needsProofCount", cards.stream().filter(ReservationCard::needsAfterProof).count());
        return "reservations";
    }

    @PostMapping("/reservations")
    public String book(
        @RequestParam long roomId,
        @RequestParam String date,
        @RequestParam String startTime,
        @RequestParam String endTime,
        @RequestParam String purpose,
        @RequestParam int peopleCount,
        Principal principal
    ) {
        Member member = memberContext.require(principal);
        try {
            BookingRequest request = new BookingRequest(
                roomId,
                LocalDate.parse(date),
                LocalTime.parse(startTime),
                LocalTime.parse(endTime),
                purpose,
                peopleCount
            );
            reservationService.book(member.id(), request);
            return "redirect:/reservations?notice=booked";
        } catch (BookingException exception) {
            return "redirect:/home?error=" + exception.code() + "&scheduleDate=" + date + "#booking";
        } catch (RuntimeException exception) {
            return "redirect:/home?error=invalid-reservation&scheduleDate=" + date + "#booking";
        }
    }

    @PostMapping("/reservations/{id}/cancel")
    public String cancel(
        @PathVariable long id,
        @RequestParam(defaultValue = "reservations") String returnTo,
        Principal principal
    ) {
        Member member = memberContext.require(principal);
        boolean returnHome = returnTo.equals("home");
        try {
            reservationService.cancel(id, member.id());
            return returnHome
                ? "redirect:/home?notice=cancelled#today-schedule"
                : "redirect:/reservations?notice=cancelled";
        } catch (BookingException exception) {
            return returnHome
                ? "redirect:/home?error=cancel#today-schedule"
                : "redirect:/reservations?error=cancel";
        }
    }

    @PostMapping("/reservations/{id}/photos")
    public String uploadPhoto(
        @PathVariable long id,
        @RequestParam String phase,
        @RequestParam MultipartFile photo,
        Principal principal
    ) {
        Member member = memberContext.require(principal);
        try {
            reservationService.savePhoto(id, member.id(), phase, photo);
            return "redirect:/reservations?notice=photo-" + phase + "#usage-proof";
        } catch (BookingException exception) {
            return "redirect:/reservations?error=" + exception.code() + "#usage-proof";
        }
    }

    @GetMapping("/reservations/{id}/photos")
    public ResponseEntity<byte[]> photo(
        @PathVariable long id,
        @RequestParam String phase,
        Principal principal
    ) {
        Member member = memberContext.require(principal);
        if (!(phase.equals("before") || phase.equals("after"))) return ResponseEntity.badRequest().build();
        PhotoData photo = reservations.findPhoto(id, member.id(), phase).orElse(null);
        if (photo == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, photo.mimeType())
            .cacheControl(CacheControl.noStore())
            .body(photo.bytes());
    }

    public record ReservationCard(
        ReservationView reservation,
        String date,
        String range,
        String stateLabel,
        String stateClass,
        boolean cancellable,
        boolean beforeProofAvailable,
        String beforeProofHint,
        boolean afterProofAvailable,
        String afterProofHint,
        boolean activeReservation,
        boolean needsAfterProof
    ) {
        static ReservationCard from(ReservationView reservation, Instant now) {
            Instant startsAt = reservation.startsAt().toInstant();
            Instant endsAt = reservation.endsAt().toInstant();
            boolean upcoming = startsAt.isAfter(now);
            boolean active = !upcoming && endsAt.isAfter(now);
            boolean beforeProofAvailable = !reservation.beforePhoto()
                && !now.isBefore(startsAt.minusSeconds(5 * 60L))
                && now.isBefore(endsAt);
            boolean afterProofAvailable = !reservation.afterPhoto() && !now.isBefore(endsAt);
            boolean needsAfter = !endsAt.isAfter(now) && !reservation.afterPhoto();
            String beforeProofHint;
            if (reservation.beforePhoto()) {
                beforeProofHint = "인증 완료";
            } else if (now.isBefore(startsAt.minusSeconds(5 * 60L))) {
                beforeProofHint = ViewFormats.time(reservation.startsAt().minusMinutes(5)) + "부터 활성화";
            } else if (now.isBefore(endsAt)) {
                beforeProofHint = "지금 인증 가능";
            } else {
                beforeProofHint = "인증 가능 시간 종료";
            }
            String afterProofHint = reservation.afterPhoto()
                ? "인증 완료"
                : now.isBefore(endsAt)
                    ? ViewFormats.time(reservation.endsAt()) + "부터 활성화"
                    : "지금 인증 가능";
            String label;
            String css;
            if (reservation.afterPhoto()) {
                label = "이용 완료";
                css = "completed";
            } else if (upcoming) {
                label = reservation.beforePhoto() ? "시작 준비 완료" : beforeProofAvailable ? "시작 인증 가능" : "예약 예정";
                css = "upcoming";
            } else if (active) {
                label = reservation.beforePhoto() ? "이용 중" : "이용 전 인증 필요";
                css = reservation.beforePhoto() ? "active" : "attention";
            } else {
                label = "이용 후 인증 필요";
                css = "attention";
            }
            return new ReservationCard(
                reservation,
                ViewFormats.date(reservation.startsAt()),
                ViewFormats.range(reservation.startsAt(), reservation.endsAt()),
                label,
                css,
                upcoming,
                beforeProofAvailable,
                beforeProofHint,
                afterProofAvailable,
                afterProofHint,
                active,
                needsAfter
            );
        }
    }
}
