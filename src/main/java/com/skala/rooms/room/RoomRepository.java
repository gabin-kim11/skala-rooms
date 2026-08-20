package com.skala.rooms.room;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class RoomRepository {

    private final JdbcClient jdbc;

    public RoomRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public List<Room> findActive() {
        return jdbc.sql("""
                SELECT id, room_code, name, floor, capacity, features
                  FROM rooms
                 WHERE active = TRUE
                 ORDER BY sort_order, room_code
                """)
            .query((rs, rowNum) -> mapRoom(rs))
            .list();
    }

    public Optional<Room> findActiveById(long id) {
        return jdbc.sql("""
                SELECT id, room_code, name, floor, capacity, features
                  FROM rooms
                 WHERE id = :id AND active = TRUE
                """)
            .param("id", id)
            .query((rs, rowNum) -> mapRoom(rs))
            .optional();
    }

    private Room mapRoom(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Room(
            rs.getLong("id"),
            rs.getString("room_code"),
            rs.getString("name"),
            rs.getString("floor"),
            rs.getInt("capacity"),
            rs.getString("features")
        );
    }
}
