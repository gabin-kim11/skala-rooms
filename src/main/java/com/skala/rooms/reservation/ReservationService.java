package com.skala.rooms.reservation;

import com.skala.rooms.SkalaRoomsApplication;
import com.skala.rooms.room.Room;
import com.skala.rooms.room.RoomRepository;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ReservationService {

    private static final int DAILY_LIMIT_MINUTES = 180;
    private static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final ReservationRepository reservations;
    private final RoomRepository rooms;
    private final JdbcTemplate jdbc;

    public ReservationService(ReservationRepository reservations, RoomRepository rooms, JdbcTemplate jdbc) {
        this.reservations = reservations;
        this.rooms = rooms;
        this.jdbc = jdbc;
    }

    @Transactional
    public void book(long memberId, BookingRequest request) {
        validateBasic(request);
        Room room = rooms.findActiveById(request.roomId())
            .orElseThrow(() -> new BookingException("capacity"));
        if (request.peopleCount() > room.capacity()) throw new BookingException("capacity");

        OffsetDateTime startsAt = ZonedDateTime.of(request.date(), request.startTime(), SkalaRoomsApplication.SEOUL).toOffsetDateTime();
        OffsetDateTime endsAt = ZonedDateTime.of(request.date(), request.endTime(), SkalaRoomsApplication.SEOUL).toOffsetDateTime();
        if (!startsAt.toInstant().isAfter(java.time.Instant.now())) throw new BookingException("past");

        lockMemberDay(memberId, request.date());
        long durationMinutes = Duration.between(startsAt, endsAt).toMinutes();
        if (reservations.usedMinutes(memberId, request.date()) + durationMinutes > DAILY_LIMIT_MINUTES) {
            throw new BookingException("daily-limit");
        }

        try {
            reservations.create(memberId, request, startsAt, endsAt);
        } catch (DataIntegrityViolationException exception) {
            throw new BookingException("overlap");
        }
    }

    @Transactional
    public void cancel(long reservationId, long memberId) {
        if (!reservations.cancel(reservationId, memberId)) throw new BookingException("cancel");
    }

    @Transactional
    public void savePhoto(long reservationId, long memberId, String phase, MultipartFile photo) {
        if (!(phase.equals("before") || phase.equals("after")) || photo.isEmpty()
            || photo.getSize() > MAX_FILE_SIZE || !IMAGE_TYPES.contains(photo.getContentType())) {
            throw new BookingException("photo-format");
        }
        ReservationView reservation = reservations.findOwnedApproved(reservationId, memberId)
            .orElseThrow(() -> new BookingException("photo-owner"));
        validatePhotoTiming(reservation, phase, Instant.now());
        try {
            byte[] bytes = photo.getBytes();
            if (!hasValidSignature(bytes, photo.getContentType())) throw new BookingException("photo-format");
            reservations.savePhoto(reservationId, memberId, phase, photo.getContentType(), bytes);
        } catch (IOException exception) {
            throw new BookingException("photo");
        }
    }

    static void validatePhotoTiming(ReservationView reservation, String phase, Instant now) {
        Instant startsAt = reservation.startsAt().toInstant();
        Instant endsAt = reservation.endsAt().toInstant();
        if (phase.equals("before")) {
            if (now.isBefore(startsAt.minus(Duration.ofMinutes(5)))) {
                throw new BookingException("photo-too-early");
            }
            if (!now.isBefore(endsAt)) {
                throw new BookingException("photo-window-closed");
            }
            return;
        }
        if (now.isBefore(endsAt)) {
            throw new BookingException("photo-after-too-early");
        }
    }

    private void validateBasic(BookingRequest request) {
        LocalDate today = LocalDate.now(SkalaRoomsApplication.SEOUL);
        if (request.date() == null || request.startTime() == null || request.endTime() == null
            || request.date().isBefore(today) || request.date().isAfter(today.plusDays(30))
            || request.purpose() == null || request.purpose().isBlank() || request.purpose().length() > 160
            || request.peopleCount() < 1 || request.peopleCount() > 12) {
            throw new BookingException("invalid-reservation");
        }
        long minutes = Duration.between(request.startTime(), request.endTime()).toMinutes();
        if (minutes <= 0 || minutes > DAILY_LIMIT_MINUTES || minutes % 30 != 0
            || request.startTime().getMinute() % 30 != 0 || request.endTime().getMinute() % 30 != 0) {
            throw new BookingException("invalid-reservation");
        }
        if (request.startTime().isBefore(LocalTime.of(9, 0)) || request.endTime().isAfter(LocalTime.of(18, 0))) {
            throw new BookingException("hours");
        }
    }

    private void lockMemberDay(long memberId, LocalDate date) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtext(?))")) {
                statement.setString(1, memberId + ":" + date);
                statement.execute();
            }
            return null;
        });
    }

    private boolean hasValidSignature(byte[] bytes, String mimeType) {
        if (mimeType.equals("image/jpeg")) {
            return bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
        }
        if (mimeType.equals("image/png")) {
            return bytes.length >= 4 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47;
        }
        return bytes.length >= 12
            && new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF")
            && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP");
    }
}
