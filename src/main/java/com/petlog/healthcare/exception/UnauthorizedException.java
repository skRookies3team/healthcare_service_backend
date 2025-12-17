package com.petlog.record.exception;

// UnauthorizedException은 보통 권한이 없을 때 403 FORBIDDEN으로 처리합니다.
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}