package com.skydetect.backend.auth;

import com.skydetect.backend.user.Role;
import com.skydetect.backend.user.User;
import com.skydetect.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 최초 운영자 계정을 만든다.
 *
 * <p>회원가입 API 를 두지 않기로 했으므로(관제실 운영자 한 종류, 외부 가입 없음)
 * 계정이 하나도 없으면 로그인 자체가 불가능하다. 그 최초 한 명을 여기서 만든다.
 *
 * <p>비밀번호는 코드에 넣지 않고 application-local.yaml 에서 읽는다.
 * 설정이 없으면 아무것도 하지 않는다.
 */
@Component
public class InitialOperatorSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialOperatorSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;

    public InitialOperatorSeeder(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AuthProperties.InitialOperator initial = authProperties.initialOperator();

        if (initial == null
                || !StringUtils.hasText(initial.username())
                || !StringUtils.hasText(initial.password())) {
            log.info("초기 운영자 계정 설정이 없어 시딩을 건너뛴다.");
            return;
        }

        if (userRepository.existsByUsername(initial.username())) {
            log.info("운영자 계정 '{}' 이(가) 이미 있어 시딩을 건너뛴다.", initial.username());
            return;
        }

        userRepository.save(User.builder()
                .username(initial.username())
                .password(passwordEncoder.encode(initial.password()))
                .role(Role.ROLE_OPERATOR)
                .build());

        log.info("초기 운영자 계정 '{}' 을(를) 생성했다.", initial.username());
    }
}
