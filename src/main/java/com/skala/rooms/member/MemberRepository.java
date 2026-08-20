package com.skala.rooms.member;

import java.util.List;
import java.util.Optional;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class MemberRepository {

    private final JdbcClient jdbc;

    public MemberRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Member> findByCode(String memberCode) {
        List<Member> members = jdbc.sql("""
                SELECT id, member_code, name, password_hash
                  FROM members
                 WHERE member_code = :memberCode
                """)
            .param("memberCode", memberCode)
            .query((rs, rowNum) -> new Member(
                rs.getLong("id"),
                rs.getString("member_code"),
                rs.getString("name"),
                rs.getString("password_hash")
            ))
            .list();
        return members.stream().findFirst();
    }

    public Optional<Member> findByEmail(String email) {
        return jdbc.sql("""
                SELECT id, member_code, name, password_hash
                  FROM members
                 WHERE LOWER(email) = :email
                """)
            .param("email", email)
            .query((rs, rowNum) -> new Member(
                rs.getLong("id"),
                rs.getString("member_code"),
                rs.getString("name"),
                rs.getString("password_hash")
            ))
            .optional();
    }

    public boolean existsByCode(String memberCode) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM members WHERE member_code = :memberCode)")
            .param("memberCode", memberCode)
            .query(Boolean.class)
            .single();
    }

    public boolean existsByEmail(String email) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM members WHERE LOWER(email) = :email)")
            .param("email", email)
            .query(Boolean.class)
            .single();
    }

    public Member create(String memberCode, String name, String email, String passwordHash) throws DuplicateKeyException {
        Long id = jdbc.sql("""
                INSERT INTO members (member_code, name, email, password_hash)
                VALUES (:memberCode, :name, :email, :passwordHash)
                RETURNING id
                """)
            .param("memberCode", memberCode)
            .param("name", name)
            .param("email", email)
            .param("passwordHash", passwordHash)
            .query(Long.class)
            .single();
        return new Member(id, memberCode, name, passwordHash);
    }

    public void updatePassword(long memberId, String passwordHash) {
        jdbc.sql("UPDATE members SET password_hash = :passwordHash WHERE id = :memberId")
            .param("passwordHash", passwordHash)
            .param("memberId", memberId)
            .update();
    }
}
