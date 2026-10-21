package com.skala.rooms.web;

import com.skala.rooms.member.Member;
import java.security.Principal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAdvice {

    private final MemberContext memberContext;

    public GlobalModelAdvice(MemberContext memberContext) {
        this.memberContext = memberContext;
    }

    @ModelAttribute("member")
    public Member currentMember(Principal principal, HttpServletRequest request) {
        if (principal == null || request.getRequestURI().startsWith("/google/onboarding")) return null;
        return memberContext.require(principal);
    }
}
