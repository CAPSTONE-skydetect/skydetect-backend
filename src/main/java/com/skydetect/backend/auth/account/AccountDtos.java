package com.skydetect.backend.auth.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 계정 API 요청·응답 형식. */
public final class AccountDtos {

    private AccountDtos() {
    }

    // bcrypt 는 72바이트까지만 읽는다. 그보다 긴 비밀번호는 받지 않는다.
    public record SignupRequest(
            @NotBlank(message = "아이디를 입력하세요.")
            @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다.")
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "아이디는 영문, 숫자, _ . - 만 쓸 수 있습니다.")
            String username,

            @NotBlank(message = "이메일을 입력하세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            @Size(max = 254, message = "이메일이 너무 깁니다.")
            String email,

            @NotBlank(message = "비밀번호를 입력하세요.")
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
            String password) {
    }

    public record EmailRequest(
            @NotBlank(message = "이메일을 입력하세요.")
            @Email(message = "이메일 형식이 올바르지 않습니다.")
            String email) {
    }

    public record ResetConfirmRequest(
            @NotBlank(message = "링크가 올바르지 않습니다.")
            String token,

            @NotBlank(message = "비밀번호를 입력하세요.")
            @Size(min = 8, max = 72, message = "비밀번호는 8~72자여야 합니다.")
            String newPassword) {
    }

    /** 찾기 요청 응답. 가입 여부와 무관하게 같은 문구를 준다. */
    public record AcceptedResponse(String message) {
    }
}
