package com.petlog.healthcare.exception.advice;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 에러 응답 DTO
 */
@Data
@Builder
public class ErrorResponse {
    private boolean success;
    private ErrorDetail error;
    private LocalDateTime timestamp;

    @Data
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
        private String detail;
        private List<FieldError> fieldErrors;
    }

    @Data
    @Builder
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}