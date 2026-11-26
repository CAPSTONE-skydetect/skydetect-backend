package com.skydetect.backend.auth.account;

import com.skydetect.backend.auth.AuthProperties;
import com.skydetect.backend.user.User;
import com.skydetect.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final Instant start = Instant.parse("2026-11-26T00:00:00Z");

    private UserRepository users;
    private PasswordResetTokenRepository tokens;
    private AuthMailSender mail;
    private MutableClock clock;
    private AccountService service;

    @BeforeEach
    void setUp() {
        users = mock(UserRepository.class);
        tokens = mock(PasswordResetTokenRepository.class);
        mail = mock(AuthMailSender.class);
        clock = new MutableClock(start);
        var properties = new AuthProperties(Duration.ofHours(12), null, "http://site", Duration.ofMinutes(30));
        service = new AccountService(users, tokens, encoder, mail, properties, clock);
    }

    @Test
    void signupStoresNormalizedEmailAndEncodedPassword() {
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> call.getArgument(0));

        User saved = service.signup(" newbie ", " New@Mail.COM ", "password123");

        assertThat(saved.getUsername()).isEqualTo("newbie");
        assertThat(saved.getEmail()).isEqualTo("new@mail.com");
        assertThat(saved.getPassword()).startsWith("{bcrypt}");
        assertThat(encoder.matches("password123", saved.getPassword())).isTrue();
    }

    @Test
    void signupRejectsTakenUsernameAndEmail() {
        when(users.existsByUsername("taken")).thenReturn(true);
        assertThatThrownBy(() -> service.signup("taken", "a@b.com", "password123"))
                .isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("USERNAME_TAKEN"));

        when(users.existsByEmail("used@b.com")).thenReturn(true);
        assertThatThrownBy(() -> service.signup("fresh", "Used@B.com", "password123"))
                .isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("EMAIL_TAKEN"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void unknownEmailSendsNothingForBothRecoveryRequests() {
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());

        service.sendUsername("nobody@b.com");
        service.requestPasswordReset("nobody@b.com");

        verify(mail, never()).send(anyString(), anyString(), anyString());
        verify(tokens, never()).save(any());
    }

    @Test
    void knownEmailGetsUsernameMail() {
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user("alice", "a@b.com")));

        service.sendUsername("A@b.com");

        var body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(org.mockito.ArgumentMatchers.eq("a@b.com"), anyString(), body.capture());
        assertThat(body.getValue()).contains("alice");
    }

    @Test
    void resetLinkWorksOnceAndStoresOnlyTheHash() {
        User user = user("alice", "a@b.com");
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        when(tokens.findByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of());

        service.requestPasswordReset("a@b.com");

        var saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        var body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(anyString(), anyString(), body.capture());
        String rawToken = tokenFrom(body.getValue());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(rawToken).hasSize(64);
        assertThat(body.getValue()).contains("http://site/#/reset?token=");

        when(tokens.findByTokenHash(AccountService.hash(rawToken))).thenReturn(Optional.of(saved.getValue()));

        service.resetPassword(rawToken, "brandnew123");
        assertThat(encoder.matches("brandnew123", user.getPassword())).isTrue();

        // 같은 링크로 다시 바꿀 수 없다.
        assertThatThrownBy(() -> service.resetPassword(rawToken, "another1234"))
                .isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("INVALID_TOKEN"));
    }

    @Test
    void expiredAndUnknownTokensAreRejected() {
        User user = user("alice", "a@b.com");
        var token = new PasswordResetToken(user.getId(), AccountService.hash("old"), start, start.plus(Duration.ofMinutes(30)));
        when(tokens.findByTokenHash(AccountService.hash("old"))).thenReturn(Optional.of(token));
        clock.set(start.plus(Duration.ofMinutes(31)));

        assertThatThrownBy(() -> service.resetPassword("old", "brandnew123"))
                .isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("INVALID_TOKEN"));
        assertThatThrownBy(() -> service.resetPassword("never-issued", "brandnew123"))
                .isInstanceOfSatisfying(AccountException.class, e -> assertThat(e.code()).isEqualTo("INVALID_TOKEN"));
    }

    @Test
    void newRequestInvalidatesPreviousLink() {
        User user = user("alice", "a@b.com");
        var previous = new PasswordResetToken(user.getId(), "h", start, start.plus(Duration.ofMinutes(30)));
        when(users.findByEmail("a@b.com")).thenReturn(Optional.of(user));
        when(tokens.findByUserIdAndUsedAtIsNull(user.getId())).thenReturn(List.of(previous));

        service.requestPasswordReset("a@b.com");

        assertThat(previous.usable(start)).isFalse();
    }

    private User user(String username, String email) {
        User user = User.builder().username(username).email(email)
                .password(encoder.encode("oldpassword1")).role(com.skydetect.backend.user.Role.ROLE_OPERATOR).build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 7L);
        return user;
    }

    private static String tokenFrom(String mailBody) {
        Matcher matcher = Pattern.compile("token=([A-Za-z0-9_-]+)").matcher(mailBody);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }

    /** 시간을 옮길 수 있는 Clock. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
