package com.skala.rooms.reservation;

import com.skala.rooms.SkalaRoomsApplication;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReservationRepository {

    private static final String RESERVATION_COLUMNS = """
        SELECT r.id, r.room_id, rm.room_code, rm.name AS room_name,
               m.member_code, m.name AS member_name, r.starts_at, r.ends_at,
               r.purpose, r.people_count, r.status,
               EXISTS (SELECT 1 FROM reservation_photos bp WHERE bp.reservation_id = r.id AND bp.phase = 'before') AS before_photo,
               EXISTS (SELECT 1 FROM reservation_photos ap WHERE ap.reservation_id = r.id AND ap.phase = 'after') AS after_photo
          FROM reservations r
          JOIN rooms rm ON rm.id = r.room_id
          JOIN members m ON m.id = r.member_id
        """;

    private final JdbcClient jdbc;

    public ReservationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<ReservationView> findForDay(LocalDate date) {
        OffsetDateTime start = date.atStartOfDay(SkalaRoomsApplication.SEOUL).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(SkalaRoomsApplication.SEOUL).toOffsetDateTime();
        return jdbc.sql(RESERVATION_COLUMNS + """
                 WHERE r.status = 'approved' AND r.starts_at < :end AND r.ends_at > :start
                 ORDER BY r.starts_at
                """)
            .param("start", start)
            .param("end", end)
            .query(this::mapReservation)
            .list();
    }

    public List<ReservationView> findMine(long memberId) {
        return jdbc.sql(RESERVATION_COLUMNS + """
                 WHERE r.member_id = :memberId AND r.status = 'approved'
                   AND r.starts_at >= NOW() - INTERVAL '30 days'
                 ORDER BY CASE WHEN r.ends_at >= NOW() THEN 0 ELSE 1 END,
                          CASE WHEN r.ends_at >= NOW() THEN r.starts_at END ASC,
                          r.starts_at DESC
                 LIMIT 20
                """)
            .param("memberId", memberId)
            .query(this::mapReservation)
            .list();
    }

    public List<ReservationView> findHistoryForMember(long memberId) {
        return jdbc.sql(RESERVATION_COLUMNS + """
                 WHERE r.member_id = :memberId
                 ORDER BY r.starts_at DESC, r.id DESC
                """)
            .param("memberId", memberId)
            .query(this::mapReservation)
            .list();
    }

    public ReservationSummary findSummary(long memberId) {
        return jdbc.sql("""
                SELECT COUNT(r.id) FILTER (WHERE r.status = 'approved') AS total_count,
                       COUNT(ap.id) FILTER (WHERE r.status = 'approved') AS completed_count,
                       COALESCE(SUM(EXTRACT(EPOCH FROM (r.ends_at - r.starts_at)) / 60)
                         FILTER (WHERE r.status = 'approved' AND r.ends_at <= NOW()), 0)::BIGINT AS total_minutes
                  FROM reservations r
                  LEFT JOIN reservation_photos ap ON ap.reservation_id = r.id AND ap.phase = 'after'
                 WHERE r.member_id = :memberId
                """)
            .param("memberId", memberId)
            .query((rs, rowNum) -> new ReservationSummary(
                rs.getLong("total_count"),
                rs.getLong("completed_count"),
                rs.getLong("total_minutes")
            ))
            .optional()
            .orElse(ReservationSummary.empty());
    }

    public long usedMinutes(long memberId, LocalDate date) {
        OffsetDateTime start = date.atStartOfDay(SkalaRoomsApplication.SEOUL).toOffsetDateTime();
        OffsetDateTime end = date.plusDays(1).atStartOfDay(SkalaRoomsApplication.SEOUL).toOffsetDateTime();
        return jdbc.sql("""
                SELECT COALESCE(SUM(EXTRACT(EPOCH FROM (
                         LEAST(ends_at, :end) - GREATEST(starts_at, :start)
                       )) / 60), 0)::BIGINT
                  FROM reservations
                 WHERE member_id = :memberId AND status = 'approved'
                   AND starts_at < :end AND ends_at > :start
                """)
            .param("memberId", memberId)
            .param("start", start)
            .param("end", end)
            .query(Long.class)
            .single();
    }

    public void create(long memberId, BookingRequest request, OffsetDateTime startsAt, OffsetDateTime endsAt) {
        jdbc.sql("""
                INSERT INTO reservations
                    (room_id, member_id, starts_at, ends_at, purpose, people_count, status, approved_at)
                VALUES
                    (:roomId, :memberId, :startsAt, :endsAt, :purpose, :peopleCount, 'approved', NOW())
                """)
            .param("roomId", request.roomId())
            .param("memberId", memberId)
            .param("startsAt", startsAt)
            .param("endsAt", endsAt)
            .param("purpose", request.purpose().trim())
            .param("peopleCount", request.peopleCount())
            .update();
    }

    public boolean cancel(long reservationId, long memberId) {
        int changed = jdbc.sql("""
                UPDATE reservations SET status = 'cancelled'
                 WHERE id = :reservationId AND member_id = :memberId
                   AND status = 'approved' AND starts_at > NOW()
                """)
            .param("reservationId", reservationId)
            .param("memberId", memberId)
            .update();
        return changed == 1;
    }

    public boolean isOwnedApproved(long reservationId, long memberId) {
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM reservations
                   WHERE id = :reservationId AND member_id = :memberId AND status = 'approved'
                )
                """)
            .param("reservationId", reservationId)
            .param("memberId", memberId)
            .query(Boolean.class)
            .single();
    }

    public Optional<ReservationView> findOwnedApproved(long reservationId, long memberId) {
        return jdbc.sql(RESERVATION_COLUMNS + """
                 WHERE r.id = :reservationId
                   AND r.member_id = :memberId
                   AND r.status = 'approved'
                """)
            .param("reservationId", reservationId)
            .param("memberId", memberId)
            .query(this::mapReservation)
            .optional();
    }

    public void savePhoto(long reservationId, long memberId, String phase, String mimeType, byte[] bytes) {
        jdbc.sql("""
                INSERT INTO reservation_photos
                    (reservation_id, member_id, phase, mime_type, file_size, image_data)
                VALUES
                    (:reservationId, :memberId, :phase, :mimeType, :fileSize, :bytes)
                ON CONFLICT (reservation_id, phase) DO UPDATE SET
                    member_id = EXCLUDED.member_id,
                    mime_type = EXCLUDED.mime_type,
                    file_size = EXCLUDED.file_size,
                    image_data = EXCLUDED.image_data,
                    created_at = NOW()
                """)
            .param("reservationId", reservationId)
            .param("memberId", memberId)
            .param("phase", phase)
            .param("mimeType", mimeType)
            .param("fileSize", bytes.length)
            .param("bytes", bytes)
            .update();
    }

    public Optional<PhotoData> findPhoto(long reservationId, long memberId, String phase) {
        return jdbc.sql("""
                SELECT rp.mime_type, rp.image_data
                  FROM reservation_photos rp
                  JOIN reservations r ON r.id = rp.reservation_id
                 WHERE rp.reservation_id = :reservationId
                   AND rp.phase = :phase AND r.member_id = :memberId
                """)
            .param("reservationId", reservationId)
            .param("phase", phase)
            .param("memberId", memberId)
            .query((rs, rowNum) -> new PhotoData(rs.getString("mime_type"), rs.getBytes("image_data")))
            .optional();
    }

    private ReservationView mapReservation(ResultSet rs, int rowNum) throws SQLException {
        return new ReservationView(
            rs.getLong("id"),
            rs.getLong("room_id"),
            rs.getString("room_code"),
            rs.getString("room_name"),
            rs.getString("member_code"),
            rs.getString("member_name"),
            rs.getObject("starts_at", OffsetDateTime.class),
            rs.getObject("ends_at", OffsetDateTime.class),
            rs.getString("purpose"),
            rs.getInt("people_count"),
            rs.getString("status"),
            rs.getBoolean("before_photo"),
            rs.getBoolean("after_photo")
        );
    }
}
