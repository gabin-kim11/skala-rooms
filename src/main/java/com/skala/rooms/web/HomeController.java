package com.skala.rooms.web;

import com.skala.rooms.SkalaRoomsApplication;
import com.skala.rooms.member.Member;
import com.skala.rooms.reservation.ReservationRepository;
import com.skala.rooms.reservation.ReservationSummary;
import com.skala.rooms.reservation.ReservationView;
import com.skala.rooms.room.Room;
import com.skala.rooms.room.RoomRepository;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class HomeController {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final MemberContext memberContext;
    private final RoomRepository rooms;
    private final ReservationRepository reservations;

    public HomeController(MemberContext memberContext, RoomRepository rooms, ReservationRepository reservations) {
        this.memberContext = memberContext;
        this.rooms = rooms;
        this.reservations = reservations;
    }

    @GetMapping("/home")
    public String home(
        @RequestParam(required = false) String scheduleDate,
        Principal principal,
        Model model
    ) {
        Member member = memberContext.require(principal);
        LocalDate today = LocalDate.now(SkalaRoomsApplication.SEOUL);
        LocalDate selectedDate = parseDate(scheduleDate, today);
        List<ReservationView> selectedBookings = reservations.findForDay(selectedDate);
        List<ReservationView> todayBookings = selectedDate.equals(today)
            ? selectedBookings
            : reservations.findForDay(today);
        List<RoomDayView> roomViews = rooms.findActive().stream()
            .map(room -> buildRoomView(room, selectedDate, selectedBookings))
            .toList();
        ReservationSummary summary = reservations.findSummary(member.id());
        long usedMinutes = reservations.usedMinutes(member.id(), selectedDate);

        String weekday = selectedDate.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.KOREAN);
        model.addAttribute("active", "home");
        model.addAttribute("today", today);
        model.addAttribute("maxDate", today.plusDays(30));
        model.addAttribute("selectedDate", selectedDate);
        model.addAttribute("selectedDateLabel", selectedDate.getMonthValue() + "월 " + selectedDate.getDayOfMonth() + "일 " + weekday);
        model.addAttribute("roomViews", roomViews);
        Instant now = Instant.now();
        model.addAttribute("todayBookings", todayBookings.stream()
            .map(reservation -> ScheduleItem.from(reservation, member.memberCode(), now))
            .toList());
        model.addAttribute("summary", summary);
        model.addAttribute("usedMinutes", usedMinutes);
        model.addAttribute("usagePercent", Math.min(100L, usedMinutes * 100L / 180L));
        model.addAttribute("remainingMinutes", Math.max(0L, 180L - usedMinutes));
        model.addAttribute("maxSelectableSlots", Math.max(0L, (180L - usedMinutes) / 30L));
        return "home";
    }

    private LocalDate parseDate(String value, LocalDate today) {
        try {
            LocalDate parsed = value == null ? today : LocalDate.parse(value);
            return parsed.isBefore(today) || parsed.isAfter(today.plusDays(30)) ? today : parsed;
        } catch (RuntimeException exception) {
            return today;
        }
    }

    private RoomDayView buildRoomView(Room room, LocalDate date, List<ReservationView> allBookings) {
        List<ReservationView> bookings = allBookings.stream()
            .filter(reservation -> reservation.roomId() == room.id())
            .sorted(Comparator.comparing(ReservationView::startsAt))
            .toList();
        ZonedDateTime now = ZonedDateTime.now(SkalaRoomsApplication.SEOUL);
        List<TimeSlot> slots = new ArrayList<>();
        for (int index = 0; index < 18; index++) {
            LocalTime start = LocalTime.of(9, 0).plusMinutes(index * 30L);
            LocalTime end = start.plusMinutes(30);
            ZonedDateTime startsAt = ZonedDateTime.of(date, start, SkalaRoomsApplication.SEOUL);
            ZonedDateTime endsAt = ZonedDateTime.of(date, end, SkalaRoomsApplication.SEOUL);
            boolean past = !startsAt.isAfter(now);
            boolean booked = bookings.stream().anyMatch(reservation ->
                reservation.startsAt().isBefore(endsAt.toOffsetDateTime())
                    && reservation.endsAt().isAfter(startsAt.toOffsetDateTime()));
            slots.add(new TimeSlot(index, start.format(TIME), end.format(TIME), past || booked));
        }

        ReservationView current = bookings.stream()
            .filter(reservation -> !reservation.startsAt().isAfter(now.toOffsetDateTime()) && reservation.endsAt().isAfter(now.toOffsetDateTime()))
            .findFirst().orElse(null);
        ReservationView next = bookings.stream()
            .filter(reservation -> reservation.startsAt().isAfter(now.toOffsetDateTime()))
            .findFirst().orElse(null);
        List<String> freeRanges = freeRanges(date, bookings, now);
        List<BookingItem> bookingItems = bookings.stream().map(BookingItem::from).toList();
        return new RoomDayView(
            room,
            slots,
            bookingItems,
            freeRanges,
            current != null ? "사용 중" : date.equals(now.toLocalDate()) ? "사용 가능" : bookings.isEmpty() ? "예약 없음" : "예약 일정 있음",
            next == null ? "없음" : ViewFormats.time(next.startsAt())
        );
    }

    private List<String> freeRanges(LocalDate date, List<ReservationView> bookings, ZonedDateTime now) {
        ZonedDateTime opening = ZonedDateTime.of(date, LocalTime.of(9, 0), SkalaRoomsApplication.SEOUL);
        ZonedDateTime closing = ZonedDateTime.of(date, LocalTime.of(18, 0), SkalaRoomsApplication.SEOUL);
        ZonedDateTime cursor = opening;
        if (date.equals(now.toLocalDate()) && now.isAfter(opening)) {
            LocalDateTime rounded = now.toLocalDateTime().plusMinutes(29).withSecond(0).withNano(0);
            rounded = rounded.withMinute((rounded.getMinute() / 30) * 30);
            cursor = rounded.atZone(SkalaRoomsApplication.SEOUL);
        }
        List<String> ranges = new ArrayList<>();
        for (ReservationView booking : bookings) {
            ZonedDateTime start = booking.startsAt().atZoneSameInstant(SkalaRoomsApplication.SEOUL);
            ZonedDateTime end = booking.endsAt().atZoneSameInstant(SkalaRoomsApplication.SEOUL);
            if (!end.isAfter(cursor) || !start.isBefore(closing)) continue;
            if (start.isAfter(cursor)) ranges.add(cursor.format(TIME) + "~" + start.format(TIME));
            if (end.isAfter(cursor)) cursor = end;
        }
        if (cursor.isBefore(closing)) ranges.add(cursor.format(TIME) + "~" + closing.format(TIME));
        return ranges;
    }

    public record TimeSlot(int index, String start, String end, boolean blocked) {
    }

    public record BookingItem(String range) {
        static BookingItem from(ReservationView reservation) {
            return new BookingItem(ViewFormats.range(reservation.startsAt(), reservation.endsAt()));
        }
    }

    public record RoomDayView(
        Room room,
        List<TimeSlot> slots,
        List<BookingItem> bookings,
        List<String> freeRanges,
        String state,
        String nextTime
    ) {
    }

    public record ScheduleItem(
        long id,
        String start,
        String end,
        String roomCode,
        String title,
        String detail,
        boolean mine,
        boolean cancellable
    ) {
        static ScheduleItem from(ReservationView reservation, String currentMemberCode, Instant now) {
            boolean mine = reservation.memberCode().equals(currentMemberCode);
            return new ScheduleItem(
                reservation.id(),
                ViewFormats.time(reservation.startsAt()),
                ViewFormats.time(reservation.endsAt()),
                reservation.roomCode(),
                mine ? reservation.purpose() : "예약된 시간",
                mine
                    ? reservation.memberName() + " · " + reservation.memberCode() + " · " + reservation.peopleCount() + "명"
                    : "다른 교육생의 예약 · 상세 정보 비공개",
                mine,
                mine && reservation.startsAt().toInstant().isAfter(now)
            );
        }
    }
}
