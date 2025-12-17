package com.petlog.record.exception;

// BusinessException을 상속하도록 수정
public class ResourceNotFoundException extends BusinessException {

    // StyleService에서 이 생성자를 호출한다고 가정
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }

    // GlobalExceptionHandler에서 e.getMessage()를 사용하므로,
    // 메시지를 전달하는 생성자도 정의
    public ResourceNotFoundException(ErrorCode errorCode, String detail) {
        super(errorCode, detail);
    }
}