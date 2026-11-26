package com.skydetect.backend.auth.account;

import com.skydetect.backend.auth.AuthProperties;
import com.skydetect.backend.user.Role;
import com.skydetect.backend.user.User;
import com.skydetect.backend.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 회원가입, 아이디 찾기, 비밀번호 재설정.
 *
 * <p>찾기 계열은 이메일이 가입돼 있든 아니든 같은 응답을 준다(호출한 쪽에서 가입 여부를 알 수 없게).
 * 가입돼 있을 때만 메일을 보낸다.
 */
@Service
public class AccountService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String INVALID_TOKEN_MESSAGE = "링크가 만료되었거나 이미 사용되었습니다.";

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthMailSender mailSender;
    private final AuthProperties properties;
    private final Clock clock;

    @Autowired
    public AccountService(UserRepository users,
                          PasswordResetTokenRepository tokens,
                          PasswordEncoder passwordEncoder,
                          AuthMailSender mailSender,
                          AuthProperties properties) {
        this(users, tokens, passwordEncoder, mailSender, properties, Clock.systemUTC());
    }

    AccountService(UserRepository users,
                   PasswordResetTokenRepository tokens,
                   PasswordEncoder passwordEncoder,
                   AuthMailSender mailSender,
                   AuthProperties properties,
                   Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public User signup(String username, String email, String rawPassword) {
        String name = username.trim();
        String mail = normalize(email);

        if (users.existsByUsername(name)) {
            throw new AccountException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "이미 사용 중인 아이디입니다.");
        }
        if (users.existsByEmail(mail)) {
            throw new AccountException(HttpStatus.CONFLICT, "EMAIL_TAKEN", "이미 가입된 이메일입니다.");
        }

        try {
            // 위 검사와 저장 사이에 같은 값이 들어오면 DB 의 unique 제약이 막는다.
            return users.saveAndFlush(User.builder()
                    .username(name)
                    .email(mail)
                    .password(passwordEncoder.encode(rawPassword))
                    .role(Role.ROLE_OPERATOR)
                    .build());
        } catch (DataIntegrityViolationException e) {
            throw new AccountException(HttpStatus.CONFLICT, "ACCOUNT_TAKEN", "이미 사용 중인 아이디 또는 이메일입니다.");
        }
    }

    @Transactional(readOnly = true)
    public void sendUsername(String email) {
        users.findByEmail(normalize(email)).ifPresent(user -> mailSender.send(
                user.getEmail(),
                "[SkyDetect] 아이디 안내",
                "가입하신 아이디는 " + user.getUsername() + " 입니다."));
    }

    @Transactional
    public void requestPasswordReset(String email) {
        users.findByEmail(normalize(email)).ifPresent(user -> {
            Instant now = clock.instant();
            // 새 링크를 만들면 이전 링크는 못 쓰게 한다.
            tokens.findByUserIdAndUsedAtIsNull(user.getId()).forEach(old -> old.markUsed(now));

            String token = newToken();
            tokens.save(new PasswordResetToken(
                    user.getId(), hash(token), now, now.plus(properties.resetTokenTtl())));

            String link = properties.frontendBaseUrl() + "/#/reset?token=" + token;
            mailSender.send(user.getEmail(), "[SkyDetect] 비밀번호 재설정",
                    "아래 링크에서 새 비밀번호를 정하세요. 링크는 " + properties.resetTokenTtl().toMinutes()
                            + "분 동안 한 번만 쓸 수 있습니다.\n" + link
                            + "\n요청한 적이 없다면 이 메일을 무시하세요.");
        });
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Instant now = clock.instant();
        PasswordResetToken saved = tokens.findByTokenHash(hash(token))
                .filter(t -> t.usable(now))
                .orElseThrow(() -> new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", INVALID_TOKEN_MESSAGE));

        User user = users.findById(saved.getUserId())
                .orElseThrow(() -> new AccountException(HttpStatus.BAD_REQUEST, "INVALID_TOKEN", INVALID_TOKEN_MESSAGE));

        user.changePassword(passwordEncoder.encode(newPassword));
        saved.markUsed(now);
    }

    static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
