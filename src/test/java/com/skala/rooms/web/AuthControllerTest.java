package com.skala.rooms.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.skala.rooms.auth.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;

class AuthControllerTest {

    private final AccountService accounts = mock(AccountService.class);

    @Test
    void requiresConfiguredSignupAccessCode() {
        AuthController controller = new AuthController(accounts, "private-test-code");

        String redirect = controller.createMember("U039", "테스트", "Skala!2026", "wrong-code");

        assertThat(redirect).isEqualTo("redirect:/signup?error=access");
        verify(accounts, never()).createMember("U039", "테스트", "Skala!2026");
    }

    @Test
    void createsMemberWhenAccessCodeMatches() {
        AuthController controller = new AuthController(accounts, "private-test-code");

        String redirect = controller.createMember("u039", "테스트", "Skala!2026", "private-test-code");

        assertThat(redirect).isEqualTo("redirect:/login?notice=joined");
        verify(accounts).createMember("U039", "테스트", "Skala!2026");
    }

    @Test
    void exposesAccessCodeFieldOnlyWhenConfigured() {
        AuthController controller = new AuthController(accounts, "private-test-code");
        ConcurrentModel model = new ConcurrentModel();

        assertThat(controller.signup(null, model, null)).isEqualTo("signup");
        assertThat(model.getAttribute("signupAccessCodeRequired")).isEqualTo(true);
    }
}
