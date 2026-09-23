package com.skydetect.backend.auth.dto;

/**
 * 인증 실패 응답. 실패 원인을 상세히 알려주지 않는다.
 * (계정이 없는지 비밀번호가 틀렸는지 구분해주면 계정 존재 여부가 드러난다)
 */
public record ErrorResponse(String code, String message) {
}
