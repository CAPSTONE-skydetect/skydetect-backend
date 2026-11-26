package com.skydetect.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 인증 관련 설정값.
 *
 * @param absoluteSessionTimeout 로그인 시각 기준 절대 만료. 유휴 만료(server.servlet.session.timeout)와 별개다.
 * @param initialOperator        최초 기동 시 만들 운영자 계정. 비워두면 시딩하지 않는다.
 * @param frontendBaseUrl        비밀번호 재설정 메일에 넣을 사이트 주소. 로컬 기본값은 Vite 개발 서버다.
 * @param resetTokenTtl          비밀번호 재설정 링크 유효 시간.
 */
@ConfigurationProperties(prefix = "skydetect.auth")
public record AuthProperties(

        @DefaultValue("12h") Duration absoluteSessionTimeout,

        InitialOperator initialOperator,

        @DefaultValue("http://localhost:5173") String frontendBaseUrl,

        @DefaultValue("30m") Duration resetTokenTtl
) {

    public record InitialOperator(String username, String password) {
    }
}
