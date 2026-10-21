package com.skala.rooms.web;

import com.skala.rooms.member.Member;
import com.skala.rooms.member.MemberRepository;
import java.security.Principal;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class MemberContext {

    private final MemberRepository members;

    public MemberContext(MemberRepository members) {
        this.members = members;
    }

    public Member require(Principal principal) {
        if (principal == null) throw new IllegalStateException("로그인이 필요합니다.");
        if (principal instanceof OAuth2AuthenticationToken oauth) {
            String subject = oauth.getPrincipal().getAttribute("sub");
            return members.findByGoogleSubject(subject)
                .orElseThrow(() -> new IllegalStateException("Google 계정에 연결된 회원을 찾을 수 없습니다."));
        }
        return members.findByCode(principal.getName())
            .orElseThrow(() -> new IllegalStateException("로그인 회원을 찾을 수 없습니다."));
    }
}
