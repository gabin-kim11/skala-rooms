package com.skala.rooms.config;

import com.skala.rooms.auth.NodeScryptPasswordEncoder;
import com.skala.rooms.auth.GoogleLoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new NodeScryptPasswordEncoder();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        SessionRegistry sessionRegistry,
        GoogleLoginSuccessHandler googleLoginSuccessHandler
    ) throws Exception {
        return http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(
                    "/", "/login", "/signup", "/terms", "/privacy",
                    "/oauth2/**", "/login/oauth2/**",
                    "/css/**", "/js/**", "/skala-logo-transparent.png", "/error"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(login -> login
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .usernameParameter("memberCode")
                .passwordParameter("password")
                .defaultSuccessUrl("/home", true)
                .failureUrl("/login?error=invalid")
            )
            .oauth2Login(oauth -> oauth
                .loginPage("/login")
                .successHandler(googleLoginSuccessHandler)
                .failureUrl("/login?error=google")
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?notice=logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
            )
            .sessionManagement(session -> {
                session.sessionFixation(fixation -> fixation.migrateSession());
                session.maximumSessions(5).sessionRegistry(sessionRegistry);
            })
            .build();
    }
}
