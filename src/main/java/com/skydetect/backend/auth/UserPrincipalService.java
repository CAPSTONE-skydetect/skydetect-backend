package com.skydetect.backend.auth;

import com.skydetect.backend.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 시 Spring Security 가 호출하는 조회 지점.
 *
 * <p>비밀번호 비교는 여기서 하지 않는다. 이 클래스는 저장된 사용자를 돌려줄 뿐이고,
 * 실제 대조는 DaoAuthenticationProvider 가 PasswordEncoder 로 수행한다.
 */
@Service
public class UserPrincipalService implements UserDetailsService {

    private final UserRepository userRepository;

    public UserPrincipalService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .map(UserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("등록되지 않은 사용자"));
    }
}
