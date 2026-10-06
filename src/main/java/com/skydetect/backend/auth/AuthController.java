package com.skydetect.backend.auth;

import com.skydetect.backend.auth.dto.ErrorResponse;
import com.skydetect.backend.auth.dto.LoginRequest;
import com.skydetect.backend.auth.dto.UserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 인증 API.
 *
 * <p><b>주의 — 아래 login/logout 메서드의 본문은 실행되지 않는다.</b>
 * 두 요청은 Spring Security 필터(UsernamePasswordAuthenticationFilter, LogoutFilter)가
 * DispatcherServlet 에 닿기 전에 가로챈다. 메서드가 존재하는 이유는 단 하나,
 * springdoc 이 컨트롤러만 스캔하기 때문에 이게 없으면 API 문서에 로그인이 나타나지 않아서다.
 *
 * <p>실제 동작을 바꾸려면 {@link SecurityConfig} 를 고쳐야 한다.
 */
@Tag(name = "인증", description = "세션 쿠키 기반 로그인/로그아웃")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String STUB_MESSAGE =
            "Security 필터가 처리해야 하는 요청이다. 이 메서드가 실행됐다면 필터 설정이 잘못된 것이다.";

    @Operation(
            summary = "로그인",
            description = """
                    성공하면 JSESSIONID 세션 쿠키가 발급된다.
                    요청 형식은 JSON 이 아니라 form-urlencoded 다 (Spring Security 기본 방식).
                    """
    )
    @ApiResponse(responseCode = "200", description = "로그인 성공",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "CSRF 토큰 누락", content = @Content)
    // Spring 의 @RequestBody 가 아니라 Swagger 문서용 애노테이션이다.
    // 이걸 쓰지 않고 메서드 파라미터로 두면 form 본문이 아니라 쿼리 파라미터로 문서화된다.
    @RequestBody(
            required = true,
            content = @Content(
                    mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                    schema = @Schema(implementation = LoginRequest.class)
            )
    )
    @PostMapping(
            value = "/login",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public UserResponse login() {
        throw new IllegalStateException(STUB_MESSAGE);
    }

    @Operation(
            summary = "로그아웃",
            description = "세션을 무효화하고 JSESSIONID 쿠키를 삭제한다."
    )
    @ApiResponse(responseCode = "204", description = "로그아웃 완료")
    @PostMapping("/logout")
    public void logout() {
        throw new IllegalStateException(STUB_MESSAGE);
    }

    @Operation(
            summary = "현재 로그인한 사용자 조회",
            description = """
                    프론트가 새로고침 후 세션이 살아있는지 확인할 때 쓴다.
                    세션이 없으면 이 요청은 컨트롤러에 닿기 전에 401 로 끊긴다.
                    """
    )
    @ApiResponse(responseCode = "200", description = "조회 성공",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @ApiResponse(responseCode = "401", description = "미인증 또는 세션 만료",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    @GetMapping(value = "/me", produces = MediaType.APPLICATION_JSON_VALUE)
    public UserResponse me(@AuthenticationPrincipal UserPrincipal principal) {
        return new UserResponse(principal.getUsername(), principal.getRole());
    }
}
