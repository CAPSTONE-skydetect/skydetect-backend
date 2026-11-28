package com.skydetect.backend.auth.account;

import com.skydetect.backend.auth.account.AccountDtos.AcceptedResponse;
import com.skydetect.backend.auth.account.AccountDtos.EmailRequest;
import com.skydetect.backend.auth.account.AccountDtos.ResetConfirmRequest;
import com.skydetect.backend.auth.account.AccountDtos.SignupRequest;
import com.skydetect.backend.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 로그인 전에 쓰는 계정 API. 경로는 SecurityConfig 에서 인증 없이 열어 둔다. */
@Tag(name = "계정", description = "회원가입, 아이디 찾기, 비밀번호 재설정")
@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private static final String SENT = "입력한 이메일로 가입된 계정이 있으면 안내 메일을 보냈습니다.";

    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @Operation(summary = "회원가입", description = "만들면 바로 로그인할 수 있다. 로그인은 /api/auth/login 으로 따로 한다.")
    @PostMapping("/signup")
    public ResponseEntity<UserResponse> signup(@Valid @RequestBody SignupRequest request) {
        var user = accounts.signup(request.username(), request.email(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UserResponse(user.getUsername(), user.getRole().name()));
    }

    @Operation(summary = "아이디 찾기", description = "가입된 이메일이면 아이디를 메일로 보낸다. 가입 여부와 무관하게 같은 응답.")
    @PostMapping("/find-username")
    public ResponseEntity<AcceptedResponse> findUsername(@Valid @RequestBody EmailRequest request) {
        accounts.sendUsername(request.email());
        return ResponseEntity.accepted().body(new AcceptedResponse(SENT));
    }

    @Operation(summary = "비밀번호 재설정 메일 요청", description = "가입된 이메일이면 1회용 재설정 링크를 보낸다. 가입 여부와 무관하게 같은 응답.")
    @PostMapping("/password-reset/request")
    public ResponseEntity<AcceptedResponse> requestReset(@Valid @RequestBody EmailRequest request) {
        accounts.requestPasswordReset(request.email());
        return ResponseEntity.accepted().body(new AcceptedResponse(SENT));
    }

    @Operation(summary = "비밀번호 재설정", description = "메일 링크의 토큰으로 새 비밀번호를 정한다.")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody ResetConfirmRequest request) {
        accounts.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
