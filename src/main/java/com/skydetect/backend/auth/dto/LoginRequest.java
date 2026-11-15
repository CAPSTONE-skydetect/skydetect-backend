package com.skydetect.backend.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 로그인 요청 형식. <b>실제 바인딩에는 쓰이지 않는다.</b>
 *
 * <p>로그인은 Spring Security 의 UsernamePasswordAuthenticationFilter 가 가로채서
 * 직접 파라미터를 읽는다. 이 타입은 Swagger 문서에 입력 폼을 그리기 위한 명세일 뿐이다.
 */
@Schema(description = "로그인 요청 (application/x-www-form-urlencoded)")
public record LoginRequest(

        @Schema(description = "운영자 아이디", example = "operator", requiredMode = Schema.RequiredMode.REQUIRED)
        String username,

        // 비밀번호는 예시값을 두지 않는다. 이 파일은 공개 저장소에 커밋된다.
        @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
        String password
) {
}
