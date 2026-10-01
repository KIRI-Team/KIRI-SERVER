package com.kiri.server.global.config;

import com.kiri.server.global.security.handler.CustomAccessDeniedHandler;
import com.kiri.server.global.security.handler.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    // 인증 없이 접근 가능한 경로
    private final static String[] PUBLIC_URLS={
            // 처리되지 않은 예외 포워딩
            "/error",
            // Swagger
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
    };

    @Bean
    public SecurityFilterChain springFilterChain(HttpSecurity http){
        http
                // JWT 기반 인증이라 세션, 쿠키를 쓰지 않으므로 비활성화
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_URLS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
