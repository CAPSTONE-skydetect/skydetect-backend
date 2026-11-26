package com.skydetect.backend.auth.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 메일을 보내지 않고 로그에 찍는다. 로컬 개발에서 재설정 링크를 확인하려는 용도다.
 *
 * <p><b>운영에서 쓰면 안 된다.</b> 재설정 링크가 로그에 남으면 로그를 보는 사람이 누구의 비밀번호든
 * 바꿀 수 있다. 운영에 올리기 전에 SMTP 구현으로 교체한다.
 */
@Component
@ConditionalOnProperty(name = "skydetect.auth.mail.mode", havingValue = "log", matchIfMissing = true)
public class LoggingAuthMailSender implements AuthMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthMailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.warn("[메일 발송 대체: 로그 출력] 받는 사람={} 제목={}\n{}", to, subject, body);
    }
}
