package com.skala.rooms.web;

import com.skala.rooms.auth.AccountException;
import com.skala.rooms.auth.AccountService;
import com.skala.rooms.auth.PasswordPolicy;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
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
    public String signup(@RequestParam(required = false) String memberCode, Model model, Principal principal) {
        if (principal != null) return "redirect:/home";
        model.addAttribute("memberCode", memberCode == null ? "" : memberCode);
        model.addAttribute("signupAccessCodeRequired", !signupAccessCode.isBlank());
        return "signup";
    }

    @PostMapping("/signup")
    public String createMember(
        @RequestParam String memberCode,
        @RequestParam String name,
        @RequestParam String password,
        @RequestParam(required = false) String accessCode
    ) {
        if (!hasValidSignupAccessCode(accessCode)) return "redirect:/signup?error=access";
        String normalizedCode = memberCode.trim().toUpperCase(Locale.ROOT);
        String normalizedName = name.trim();
        if (!normalizedCode.matches("^U\\d{3,}$")) return "redirect:/signup?error=format";
        if (normalizedName.isBlank() || normalizedName.length() > 80) return "redirect:/signup?error=required";
        if (!PasswordPolicy.isValid(password)) return "redirect:/signup?error=password";
        try {
            accounts.createMember(normalizedCode, normalizedName, password);
            return "redirect:/login?notice=joined";
        } catch (AccountException exception) {
            return "redirect:/signup?error=" + exception.code();
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
