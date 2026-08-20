package com.skala.rooms.auth;

import com.skala.rooms.member.Member;
import com.skala.rooms.member.MemberRepository;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final Pattern EMAIL = Pattern.compile(
        "^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$",
        Pattern.CASE_INSENSITIVE
    );

    private final JdbcClient jdbc;
    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final SecureTokenService tokens;
    private final AccountMailService mail;
    private final SessionRegistry sessions;

    public AccountService(
        JdbcClient jdbc,
        MemberRepository members,
        PasswordEncoder passwordEncoder,
        SecureTokenService tokens,
        AccountMailService mail,
        SessionRegistry sessions
    ) {
        this.jdbc = jdbc;
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.mail = mail;
        this.sessions = sessions;
    }

    public boolean isMailReady() {
        return mail.isReady();
    }

    public void createMember(String memberCode, String name, String password) {
        if (members.existsByCode(memberCode)) {
            throw new AccountException("duplicate");
        }
        try {
            members.create(memberCode, name, null, passwordEncoder.encode(password));
        } catch (DuplicateKeyException exception) {
            throw new AccountException("duplicate");
        }
    }

    public void requestSignup(String memberCode, String name, String email, String password) {
        if (!mail.isReady()) throw new AccountException("mail-unavailable");
        if (members.existsByCode(memberCode) || members.existsByEmail(email)) {
            throw new AccountException("duplicate");
        }
        boolean tooSoon = jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM pending_signups
                   WHERE (member_code = :memberCode OR LOWER(email) = :email)
                     AND created_at > NOW() - INTERVAL '1 minute'
                )
                """)
            .param("memberCode", memberCode)
            .param("email", email)
            .query(Boolean.class)
            .single();
        if (tooSoon) throw new AccountException("email-wait");

        jdbc.sql("DELETE FROM pending_signups WHERE expires_at <= NOW()")
            .update();
        jdbc.sql("DELETE FROM pending_signups WHERE member_code = :memberCode OR LOWER(email) = :email")
            .param("memberCode", memberCode)
            .param("email", email)
            .update();

        String rawToken = tokens.create();
        String tokenHash = tokens.hash(rawToken);
        try {
            jdbc.sql("""
                    INSERT INTO pending_signups
                        (member_code, name, email, password_hash, token_hash, expires_at)
                    VALUES
                        (:memberCode, :name, :email, :passwordHash, :tokenHash, NOW() + INTERVAL '10 minutes')
                    """)
                .param("memberCode", memberCode)
                .param("name", name)
                .param("email", email)
                .param("passwordHash", passwordEncoder.encode(password))
                .param("tokenHash", tokenHash)
                .update();
            mail.sendSignupVerification(email, name, rawToken);
        } catch (DuplicateKeyException exception) {
            throw new AccountException("duplicate");
        } catch (AccountException exception) {
            jdbc.sql("DELETE FROM pending_signups WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .update();
            throw exception;
        }
    }

    @Transactional
    public void verifySignup(String rawToken) {
        PendingSignup pending = jdbc.sql("""
                SELECT member_code, name, email, password_hash
                  FROM pending_signups
                 WHERE token_hash = :tokenHash AND expires_at > NOW()
                 FOR UPDATE
                """)
            .param("tokenHash", tokens.hash(rawToken))
            .query((rs, rowNum) -> new PendingSignup(
                rs.getString("member_code"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getString("password_hash")
            ))
            .optional()
            .orElseThrow(() -> new AccountException("invalid-token"));
        try {
            members.create(pending.memberCode(), pending.name(), pending.email(), pending.passwordHash());
        } catch (DuplicateKeyException exception) {
            throw new AccountException("duplicate");
        }
        jdbc.sql("DELETE FROM pending_signups WHERE token_hash = :tokenHash")
            .param("tokenHash", tokens.hash(rawToken))
            .update();
    }

    public void requestPasswordReset(String email) {
        if (!mail.isReady()) throw new AccountException("mail-unavailable");
        Member member = members.findByEmail(email).orElse(null);
        if (member == null) return;

        boolean tooSoon = jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM password_reset_tokens
                   WHERE member_id = :memberId AND created_at > NOW() - INTERVAL '1 minute'
                )
                """)
            .param("memberId", member.id())
            .query(Boolean.class)
            .single();
        if (tooSoon) return;

        jdbc.sql("DELETE FROM password_reset_tokens WHERE expires_at <= NOW() OR member_id = :memberId")
            .param("memberId", member.id())
            .update();
        String rawToken = tokens.create();
        String tokenHash = tokens.hash(rawToken);
        jdbc.sql("""
                INSERT INTO password_reset_tokens (token_hash, member_id, expires_at)
                VALUES (:tokenHash, :memberId, NOW() + INTERVAL '10 minutes')
                """)
            .param("tokenHash", tokenHash)
            .param("memberId", member.id())
            .update();
        try {
            mail.sendPasswordReset(email, rawToken);
        } catch (AccountException exception) {
            jdbc.sql("DELETE FROM password_reset_tokens WHERE token_hash = :tokenHash")
                .param("tokenHash", tokenHash)
                .update();
            throw exception;
        }
    }

    public boolean isResetTokenValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        return jdbc.sql("""
                SELECT EXISTS (
                  SELECT 1 FROM password_reset_tokens
                   WHERE token_hash = :tokenHash AND used_at IS NULL AND expires_at > NOW()
                )
                """)
            .param("tokenHash", tokens.hash(rawToken))
            .query(Boolean.class)
            .single();
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        ResetAccount account = jdbc.sql("""
                SELECT prt.member_id, m.member_code
                  FROM password_reset_tokens prt
                  JOIN members m ON m.id = prt.member_id
                 WHERE prt.token_hash = :tokenHash AND prt.used_at IS NULL AND prt.expires_at > NOW()
                 FOR UPDATE
                """)
            .param("tokenHash", tokens.hash(rawToken))
            .query((rs, rowNum) -> new ResetAccount(rs.getLong("member_id"), rs.getString("member_code")))
            .optional()
            .orElseThrow(() -> new AccountException("invalid-token"));
        members.updatePassword(account.memberId(), passwordEncoder.encode(newPassword));
        jdbc.sql("""
                UPDATE password_reset_tokens SET used_at = NOW()
                 WHERE token_hash = :tokenHash
                """)
            .param("tokenHash", tokens.hash(rawToken))
            .update();
        sessions.getAllPrincipals().stream()
            .filter(principal -> principal instanceof UserDetails details
                && details.getUsername().equals(account.memberCode()))
            .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
            .forEach(session -> session.expireNow());
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isEmailValid(String email) {
        return email != null && email.length() <= 254 && EMAIL.matcher(email).matches();
    }

    private record PendingSignup(String memberCode, String name, String email, String passwordHash) {
    }

    private record ResetAccount(long memberId, String memberCode) {
    }
}
