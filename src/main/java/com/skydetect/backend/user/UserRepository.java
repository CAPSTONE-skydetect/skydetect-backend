package com.skydetect.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** 이메일은 소문자로 정규화해 저장하므로 호출하는 쪽도 소문자로 넘긴다. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
