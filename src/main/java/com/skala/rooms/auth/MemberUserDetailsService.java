package com.skala.rooms.auth;

import com.skala.rooms.member.Member;
import com.skala.rooms.member.MemberRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberUserDetailsService implements UserDetailsService {

    private final MemberRepository members;

    public MemberUserDetailsService(MemberRepository members) {
        this.members = members;
    }

    @Override
    public UserDetails loadUserByUsername(String memberCode) throws UsernameNotFoundException {
        Member member = members.findByCode(memberCode.trim().toUpperCase(Locale.ROOT))
            .orElseThrow(() -> new UsernameNotFoundException("등록되지 않은 교육생 번호입니다."));
        return User.withUsername(member.memberCode())
            .password(member.passwordHash())
            .roles("MEMBER")
            .build();
    }
}
