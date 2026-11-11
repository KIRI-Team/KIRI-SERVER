package com.kiri.server.global.security.jwt;

import com.kiri.server.admin.auth.repository.AdminRepository;
import com.kiri.server.global.apiPayload.code.AuthErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// 관리자 토큰만 인증한다. 비활성화된 관리자는 토큰이 살아 있어도 거부한다.
@Slf4j
@RequiredArgsConstructor
public class AdminJwtAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AdminJwtProvider adminJwtProvider;
    private final AdminRepository adminRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            authenticate(request, header.substring(BEARER_PREFIX.length()));
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String token) {
        try {
            Long adminId = adminJwtProvider.getAdminIdFromAccessToken(token);
            if (!adminRepository.existsByIdAndEnabledTrue(adminId)) {
                request.setAttribute(JwtAuthFilter.EXCEPTION_ATTRIBUTE, AuthErrorCode.INVALID_TOKEN);
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                    adminId, null, List.of(new SimpleGrantedAuthority(ROLE_ADMIN))));
        } catch (ExpiredJwtException e) {
            request.setAttribute(JwtAuthFilter.EXCEPTION_ATTRIBUTE, AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("[AdminJwtAuthFilter] 유효하지 않은 토큰: uri={}, reason={}",
                    request.getRequestURI(), e.getClass().getSimpleName());
            request.setAttribute(JwtAuthFilter.EXCEPTION_ATTRIBUTE, AuthErrorCode.INVALID_TOKEN);
        }
    }
}
