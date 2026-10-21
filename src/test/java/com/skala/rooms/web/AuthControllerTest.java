package com.skala.rooms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.skala.rooms.auth.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;

class AuthControllerTest {

    private final AccountService accounts = mock(AccountService.class);

    @Test
    void requiresConfiguredSignupAccessCode() {
        AuthController controller = new AuthController(accounts, "private-test-code");
        OAuth2AuthenticationToken authentication = googleAuthentication();

        String redirect = controller.createGoogleMember(authentication, "U039", "테스트", "wrong-code");

        assertThat(redirect).isEqualTo("redirect:/google/onboarding?error=access");
        verify(accounts, never()).createGoogleMember("U039", "테스트", "test@example.com", "google-subject");
    }

    @Test
    void createsMemberWhenAccessCodeMatches() {
        AuthController controller = new AuthController(accounts, "private-test-code");
        OAuth2AuthenticationToken authentication = googleAuthentication();

        String redirect = controller.createGoogleMember(authentication, "u039", "테스트", "private-test-code");

        assertThat(redirect).isEqualTo("redirect:/home");
        verify(accounts).createGoogleMember("U039", "테스트", "test@example.com", "google-subject");
    }

    @Test
    void sendsNewUsersToGoogleAuthorization() {
        AuthController controller = new AuthController(accounts, "private-test-code");

        assertThat(controller.signup(null)).isEqualTo("redirect:/oauth2/authorization/google");
    }

    private OAuth2AuthenticationToken googleAuthentication() {
        OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
        OAuth2User user = mock(OAuth2User.class);
        when(authentication.getPrincipal()).thenReturn(user);
        when(user.getAttribute("email")).thenReturn("test@example.com");
        when(user.getAttribute("sub")).thenReturn("google-subject");
        when(user.getAttribute("email_verified")).thenReturn(true);
        return authentication;
    }
}
