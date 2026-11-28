package com.skydetect.backend.admin;

import com.skydetect.backend.auth.AuthProperties;
import com.skydetect.backend.auth.UserPrincipal;
import com.skydetect.backend.auth.dto.ErrorResponse;
import com.skydetect.backend.user.User;
import com.skydetect.backend.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입자 목록 조회 (관리자 전용).
 *
 * <p>회원가입으로 만든 계정도 일반 계정과 같은 {@code ROLE_OPERATOR} 를 받으므로,
 * 역할만으로는 관리자를 구분할 수 없다. 그래서 설정의 최초 운영자
 * ({@code skydetect.auth.initial-operator.username}) 와 이름이 같은 계정에게만 허용한다.
 * 그 이름은 이미 존재하므로 회원가입으로 가로챌 수 없다.
 *
 * <p>응답에는 비밀번호 해시를 담지 않는다.
 */
@Tag(name = "관리", description = "관리자 전용 조회")
@RestController
@RequestMapping("/api/admin")
public class AdminUserController {

    public record AdminUserResponse(Long id, String username, String email, String role,
                                    boolean enabled, LocalDateTime createdAt) {
        static AdminUserResponse from(User user) {
            return new AdminUserResponse(user.getId(), user.getUsername(), user.getEmail(),
                    user.getRole().name(), user.isEnabled(), user.getCreatedAt());
        }
    }

    private final UserRepository users;
    private final AuthProperties auth;

    public AdminUserController(UserRepository users, AuthProperties auth) {
        this.users = users;
        this.auth = auth;
    }

    @Operation(summary = "가입자 목록", description = "최초 운영자 계정만 호출할 수 있다. 가입 순으로 반환한다.")
    @GetMapping("/users")
    public ResponseEntity<?> listUsers(@AuthenticationPrincipal UserPrincipal principal) {
        if (!isAdmin(principal)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse("ACCESS_DENIED", "접근 권한이 없습니다."));
        }
        List<AdminUserResponse> body = users.findAll(Sort.by("createdAt")).stream()
                .map(AdminUserResponse::from)
                .toList();
        return ResponseEntity.ok(body);
    }

    private boolean isAdmin(UserPrincipal principal) {
        AuthProperties.InitialOperator initial = auth.initialOperator();
        return principal != null
                && initial != null
                && StringUtils.hasText(initial.username())
                && initial.username().equals(principal.getUsername());
    }
}
