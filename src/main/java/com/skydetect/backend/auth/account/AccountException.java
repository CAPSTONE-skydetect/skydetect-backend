package com.skydetect.backend.auth.account;

import org.springframework.http.HttpStatus;

/** 계정 API 의 예상된 실패. 코드는 프론트가 문구를 고르는 데 쓴다. */
public class AccountException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AccountException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
