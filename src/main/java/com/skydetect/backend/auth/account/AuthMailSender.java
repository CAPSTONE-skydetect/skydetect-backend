package com.skydetect.backend.auth.account;

/**
 * 계정 관련 안내 메일 발송. 실제 SMTP 구현은 아직 없다.
 * 구현을 바꿔 끼우면 AccountService 는 그대로 쓴다.
 */
public interface AuthMailSender {

    void send(String to, String subject, String body);
}
