package com.skala.rooms.web;

import com.skala.rooms.auth.AccountException;
import com.skala.rooms.auth.AccountService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AuthController {

    private final AccountService accounts;
    private final String signupAccessCode;

    public AuthController(
        AccountService accounts,
        @Value("${app.signup-access-code:}") String signupAccessCode
    ) {
        this.accounts = accounts;
        this.signupAccessCode = signupAccessCode;
    }

    @GetMapping("/")
    public String index(Principal principal) {
        return principal == null ? "redirect:/login" : "redirect:/home";
    }

    @GetMapping("/login")
    public String login(Principal principal) {
        return principal == null ? "login" : "redirect:/home";
    }

    @GetMapping("/signup")
    public String signup(Principal principal) {
        if (principal != null) return "redirect:/home";
        return "redirect:/oauth2/authorization/google";
    }

    @GetMapping("/google/onboarding")
    public String googleOnboarding(OAuth2AuthenticationToken authentication, Model model) {
        if (authentication == null) return "redirect:/login";
        model.addAttribute("googleEmail", authentication.getPrincipal().getAttribute("email"));
        model.addAttribute("googleName", authentication.getPrincipal().getAttribute("name"));
        model.addAttribute("signupAccessCodeRequired", !signupAccessCode.isBlank());
        return "google-onboarding";
    }

    @PostMapping("/google/onboarding")
    public String createGoogleMember(
        OAuth2AuthenticationToken authentication,
        @RequestParam String memberCode,
        @RequestParam String name,
        @RequestParam(required = false) String accessCode
    ) {
        if (authentication == null) return "redirect:/login";
        if (!hasValidSignupAccessCode(accessCode)) return "redirect:/google/onboarding?error=access";
        String normalizedCode = memberCode.trim().toUpperCase(Locale.ROOT);
        String normalizedName = name.trim();
        if (!normalizedCode.matches("^U\\d{3,}$")) return "redirect:/google/onboarding?error=format";
        if (normalizedName.isBlank() || normalizedName.length() > 80) return "redirect:/google/onboarding?error=required";
        String email = authentication.getPrincipal().getAttribute("email");
        String subject = authentication.getPrincipal().getAttribute("sub");
        Object verified = authentication.getPrincipal().getAttribute("email_verified");
        if (email == null || subject == null || !Boolean.TRUE.equals(verified)) {
            return "redirect:/login?error=google-email";
        }
        try {
            accounts.createGoogleMember(
                normalizedCode,
                normalizedName,
                AccountService.normalizeEmail(email),
                subject
            );
            return "redirect:/home";
        } catch (AccountException exception) {
            return "redirect:/google/onboarding?error=" + exception.code();
        }
    }

    private boolean hasValidSignupAccessCode(String candidate) {
        if (signupAccessCode.isBlank()) return true;
        if (candidate == null) return false;
        return MessageDigest.isEqual(
            signupAccessCode.getBytes(StandardCharsets.UTF_8),
            candidate.getBytes(StandardCharsets.UTF_8)
        );
    }
}
