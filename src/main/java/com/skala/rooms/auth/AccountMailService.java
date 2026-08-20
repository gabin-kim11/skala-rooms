package com.skala.rooms.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AccountMailService {

    private final JavaMailSender sender;
    private final boolean enabled;
    private final String from;
    private final String baseUrl;

    public AccountMailService(
        JavaMailSender sender,
        @Value("${app.mail-enabled:false}") boolean enabled,
        @Value("${app.mail-from:}") String from,
        @Value("${app.base-url:http://localhost:8080}") String baseUrl
    ) {
        this.sender = sender;
        this.enabled = enabled;
        this.from = from;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public boolean isReady() {
        return enabled && !from.isBlank() && baseUrl.startsWith("https://");
    }

    public void sendSignupVerification(String email, String name, String token) {
        send(
            email,
            "[SKALA Rooms] 이메일 인증",
            name + "님, 아래 링크를 눌러 회원가입을 완료해주세요.\n\n"
                + baseUrl + "/verify-email?token=" + token
                + "\n\n이 링크는 10분 동안 한 번만 사용할 수 있습니다. 요청하지 않았다면 무시해주세요."
        );
    }

    public void sendPasswordReset(String email, String token) {
        send(
            email,
            "[SKALA Rooms] 비밀번호 재설정",
            "아래 링크를 눌러 비밀번호를 새로 설정해주세요.\n\n"
                + baseUrl + "/reset-password?token=" + token
                + "\n\n이 링크는 10분 동안 한 번만 사용할 수 있습니다. 요청하지 않았다면 무시해주세요."
        );
    }

    private void send(String recipient, String subject, String body) {
        if (!isReady()) throw new AccountException("mail-unavailable");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
        } catch (MailException exception) {
            throw new AccountException("mail-failed");
        }
    }
}
