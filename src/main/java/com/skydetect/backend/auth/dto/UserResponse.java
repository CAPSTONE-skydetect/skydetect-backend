package com.skydetect.backend.auth.dto;

/**
 * 로그인 성공 응답과 현재 사용자 조회 응답에 함께 쓴다.
 * 비밀번호나 내부 식별자는 담지 않는다.
 */
public record UserResponse(String username, String role) {
}
