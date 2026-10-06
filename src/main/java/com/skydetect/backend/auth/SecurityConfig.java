package com.skydetect.backend.auth;

import com.skydetect.backend.auth.dto.ErrorResponse;
import com.skydetect.backend.auth.dto.UserResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * ADR 001 (세션 쿠키 기반 인증) 의 구현 지점.
 *
 * <p>인증 흐름 전체가 이 파일 하나에 모여 있다. 로그인 성공/실패, 로그아웃,
 * 미인증 접근, 권한 부족 네 가지 응답을 모두 JSON 으로 통일한다.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    private static final String LOGIN_URL = "/api/auth/login";
    private static final String LOGOUT_URL = "/api/auth/logout";

    /**
     * API 문서 경로. 로컬에서만 springdoc 을 켜므로(application.yaml 참고)
     * 운영 환경에서는 이 경로 자체가 존재하지 않는다.
     */
    private static final String[] API_DOCS_URLS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs",
            "/v3/api-docs/**"
    };

    /**
     * CSRF 토큰을 쿠키에 담는 저장소.
     *
     * <p>{@code csrf().spa()} 가 내부적으로 같은 것을 만들지만, 로그아웃 응답에서 토큰을
     * 새로 발급하려면 필터가 쓰는 것과 <b>동일한 인스턴스</b>여야 해서 직접 들고 있는다.
     * 쿠키 이름이나 경로가 어긋나면 발급해도 소용이 없다.
     */
    private final CsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AbsoluteSessionExpirationFilter absoluteSessionExpirationFilter) throws Exception {

        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(LOGIN_URL).permitAll()
                        .requestMatchers(API_DOCS_URLS).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginProcessingUrl(LOGIN_URL)
                        .successHandler(this::onLoginSuccess)
                        .failureHandler(this::onLoginFailure)
                )
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler(this::onLogoutSuccess)
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                )
                .exceptionHandling(exception -> exception
                        // 미인증 접근에 302 로그인 페이지 리다이렉트 대신 401 을 준다 (ADR 001)
                        .authenticationEntryPoint(this::onUnauthenticated)
                        .accessDeniedHandler(this::onAccessDenied)
                )
                .sessionManagement(session -> session
                        // 로그인 시 세션 ID 를 새로 발급해 세션 고정 공격을 막는다
                        .sessionFixation(fixation -> fixation.changeSessionId())
                )
                // 쿠키 기반 CSRF 토큰 + BREACH 방어. 프론트가 XSRF-TOKEN 쿠키를 읽어
                // X-XSRF-TOKEN 헤더로 되돌려주는 방식이다.
                .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
                // 세션이 복원된 직후에 절대 만료를 검사해야 하므로 이 위치에 끼운다
                .addFilterAfter(absoluteSessionExpirationFilter, SecurityContextHolderFilter.class);

        return http.build();
    }

    /**
     * 저장 형식이 {bcrypt} 접두어를 갖는 위임 방식. 나중에 해시 알고리즘을 바꿔도
     * 기존 비밀번호를 그대로 검증할 수 있다.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private void onLoginSuccess(HttpServletRequest request,
                                HttpServletResponse response,
                                Authentication authentication) throws IOException {

        // 절대 만료 기준점. 세션 고정 방어로 새 세션이 발급된 뒤이므로 여기서 넣어야 한다.
        request.getSession().setAttribute(
                AbsoluteSessionExpirationFilter.LOGIN_TIME_ATTRIBUTE,
                Instant.now().toEpochMilli());

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        writeJson(response, HttpServletResponse.SC_OK,
                new UserResponse(principal.getUsername(), principal.getRole()));
    }

    private void onLoginFailure(HttpServletRequest request,
                                HttpServletResponse response,
                                AuthenticationException exception) throws IOException {

        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                new ErrorResponse("LOGIN_FAILED", "아이디 또는 비밀번호가 올바르지 않습니다."));
    }

    private void onLogoutSuccess(HttpServletRequest request,
                                 HttpServletResponse response,
                                 Authentication authentication) throws IOException {

        // 로그아웃 과정에서 CsrfLogoutHandler 가 CSRF 쿠키를 지운다. 그대로 두면 다음 POST
        // (대표적으로 재로그인)가 토큰 없이 나가 403 이 된다. 교대 근무처럼 로그아웃 직후
        // 다시 로그인하는 흐름이 정상 동작하도록 여기서 새 토큰을 발급해 쿠키에 실어준다.
        CsrfToken newToken = csrfTokenRepository.generateToken(request);
        csrfTokenRepository.saveToken(newToken, request, response);

        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void onUnauthenticated(HttpServletRequest request,
                                   HttpServletResponse response,
                                   AuthenticationException exception) throws IOException {

        writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                new ErrorResponse("UNAUTHENTICATED", "로그인이 필요합니다."));
    }

    private void onAccessDenied(HttpServletRequest request,
                                HttpServletResponse response,
                                AccessDeniedException exception) throws IOException {

        writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                new ErrorResponse("ACCESS_DENIED", "접근 권한이 없습니다."));
    }

    private void writeJson(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
