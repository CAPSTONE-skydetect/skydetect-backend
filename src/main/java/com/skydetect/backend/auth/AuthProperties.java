package com.skydetect.backend.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * 인증 관련 설정값.
 *
 * @param absoluteSessionTimeout 로그인 시각 기준 절대 만료. 유휴 만료(server.servlet.session.timeout)와 별개다.
 * @param initialOperator        최초 기동 시 만들 운영자 계정. 비워두면 시딩하지 않는다.
 */
@ConfigurationProperties(prefix = "skydetect.auth")
public record AuthProperties(

        @DefaultValue("12h") Duration absoluteSessionTimeout,

        InitialOperator initialOperator
) {

    public record InitialOperator(String username, String password) {
    }
}
