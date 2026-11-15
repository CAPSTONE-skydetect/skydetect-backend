package com.skydetect.backend.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * 절대 만료를 적용하는 필터.
 *
 * <p>유휴 만료(마지막 요청 후 30분)는 서블릿 컨테이너가 처리하지만, 절대 만료
 * (로그인 시각 기준 12시간)는 제공되지 않는다. ADR 001 의 트레이드오프 항목에
 * "직접 구현해야 한다"고 적힌 부분이 이 클래스다.
 *
 * <p>로그인 시각은 {@link SecurityConfig} 의 로그인 성공 처리에서 세션에 넣는다.
 * 만료된 세션은 여기서 무효화하고, 이후 인가 단계가 401 을 내보낸다.
 */
@Component
public class AbsoluteSessionExpirationFilter extends OncePerRequestFilter {

    /** 세션에 로그인 시각(epoch milli)을 담아두는 키. */
    public static final String LOGIN_TIME_ATTRIBUTE = "SKYDETECT_LOGIN_TIME";

    private final Duration absoluteTimeout;

    public AbsoluteSessionExpirationFilter(AuthProperties authProperties) {
        this.absoluteTimeout = authProperties.absoluteSessionTimeout();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (session != null && isExpired(session)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExpired(HttpSession session) {
        Object loginTime = session.getAttribute(LOGIN_TIME_ATTRIBUTE);
        if (!(loginTime instanceof Long epochMilli)) {
            return false;
        }
        Instant deadline = Instant.ofEpochMilli(epochMilli).plus(absoluteTimeout);
        return Instant.now().isAfter(deadline);
    }
}
