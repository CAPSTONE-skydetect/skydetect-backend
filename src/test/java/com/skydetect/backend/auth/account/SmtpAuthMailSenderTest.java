package com.skydetect.backend.auth.account;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpAuthMailSenderTest {

    @Test
    void sendsFromConfiguredAddress() {
        JavaMailSender delegate = mock(JavaMailSender.class);

        new SmtpAuthMailSender(delegate, "noreply@example.com", "login@example.com")
                .send("user@example.com", "제목", "본문");

        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(delegate).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("noreply@example.com");
        assertThat(message.getValue().getTo()).containsExactly("user@example.com");
        assertThat(message.getValue().getSubject()).isEqualTo("제목");
        assertThat(message.getValue().getText()).isEqualTo("본문");
    }

    @Test
    void fallsBackToLoginAccountWhenFromIsBlank() {
        JavaMailSender delegate = mock(JavaMailSender.class);

        new SmtpAuthMailSender(delegate, "", "login@example.com").send("user@example.com", "s", "b");

        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(delegate).send(message.capture());
        assertThat(message.getValue().getFrom()).isEqualTo("login@example.com");
    }

    @Test
    void sendFailureDoesNotPropagate() {
        JavaMailSender delegate = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(delegate).send(any(SimpleMailMessage.class));

        assertThatCode(() -> new SmtpAuthMailSender(delegate, "a@example.com", "")
                .send("user@example.com", "s", "b")).doesNotThrowAnyException();
    }
}
