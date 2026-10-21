package com.skala.rooms.auth;

import com.skala.rooms.member.Member;
import com.skala.rooms.member.MemberRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

@Component
public class GoogleLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final MemberRepository members;

    public GoogleLoginSuccessHandler(MemberRepository members) {
        this.members = members;
    }

    @Override
    public void onAuthenticationSuccess(
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    ) throws IOException, ServletException {
        OAuth2User user = (OAuth2User) authentication.getPrincipal();
        String subject = user.getAttribute("sub");
        String email = user.getAttribute("email");
        Object verified = user.getAttribute("email_verified");
        if (subject == null || email == null || !Boolean.TRUE.equals(verified)) {
            response.sendRedirect("/login?error=google-email");
            return;
        }

        if (members.findByGoogleSubject(subject).isPresent()) {
            response.sendRedirect("/home");
            return;
        }

        Optional<Member> legacyMember = members.findByEmail(AccountService.normalizeEmail(email));
        if (legacyMember.isPresent()) {
            members.connectGoogle(legacyMember.get().id(), subject);
            response.sendRedirect("/home");
            return;
        }

        response.sendRedirect("/google/onboarding");
    }
}
