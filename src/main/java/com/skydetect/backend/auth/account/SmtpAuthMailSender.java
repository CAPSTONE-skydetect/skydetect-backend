package com.skydetect.backend.auth.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * SMTP 로 실제 메일을 보낸다. {@code skydetect.auth.mail.mode=smtp} 일 때만 켜진다.
 *
 * <p>비동기로 보낸다. 메일 서버가 느리거나 실패해도 요청 응답이 늦어지지 않고, 응답 시간으로
 * "그 이메일이 가입돼 있는지"가 드러나지도 않는다. 실패는 로그에만 남기고 사용자에게는 알리지 않는다.
 * 받는 주소와 본문(링크 토큰 포함)은 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "skydetect.auth.mail.mode", havingValue = "smtp")
public class SmtpAuthMailSender implements AuthMailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpAuthMailSender.class);

    private final JavaMailSender mailSender;
    private final String from;

    public SmtpAuthMailSender(JavaMailSender mailSender,
                              @Value("${skydetect.auth.mail.from:}") String from,
                              @Value("${spring.mail.username:}") String username) {
        this.mailSender = mailSender;
        // Gmail 은 인증한 계정이 아닌 보내는 주소를 허용하지 않으므로 기본값은 로그인 계정이다.
        this.from = from.isBlank() ? username : from;
    }

    @Override
    @Async
    public void send(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("안내 메일 발송에 실패했다: {}", e.getClass().getSimpleName() + " " + e.getMessage());
        }
    }
}
