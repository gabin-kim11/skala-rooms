package com.skala.rooms.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skala.rooms.member.Member;
import com.skala.rooms.member.MemberRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

class GoogleLoginSuccessHandlerTest {

    private final MemberRepository members = mock(MemberRepository.class);
    private final GoogleLoginSuccessHandler handler = new GoogleLoginSuccessHandler(members);

    @Test
    void sendsFirstGoogleLoginToOnboarding() throws Exception {
        Authentication authentication = googleAuthentication();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(members.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(members.findByEmail("test@example.com")).thenReturn(Optional.empty());

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        assertThat(response.getRedirectedUrl()).isEqualTo("/google/onboarding");
    }

    @Test
    void connectsVerifiedGoogleEmailToLegacyMember() throws Exception {
        Authentication authentication = googleAuthentication();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Member member = new Member(7L, "U039", "테스트", "hash");
        when(members.findByGoogleSubject("google-subject")).thenReturn(Optional.empty());
        when(members.findByEmail("test@example.com")).thenReturn(Optional.of(member));

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(members).connectGoogle(7L, "google-subject");
        assertThat(response.getRedirectedUrl()).isEqualTo("/home");
    }

    private Authentication googleAuthentication() {
        Authentication authentication = mock(Authentication.class);
        OAuth2User user = mock(OAuth2User.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(user.getAttribute("sub")).thenReturn("google-subject");
        when(user.getAttribute("email")).thenReturn("test@example.com");
        when(user.getAttribute("email_verified")).thenReturn(true);
        return authentication;
    }
}
