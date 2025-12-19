package com.petlog.healthcare.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
public class ValidationException extends BusinessException {
    private final List<FieldError> fieldErrors;

    public ValidationException(List<FieldError> fieldErrors) {
        super(ErrorCode.VALIDATION_ERROR);
        this.fieldErrors = fieldErrors;
    }

    @RequiredArgsConstructor
    @Getter
    public static class FieldError {
        private final String field;
        private final String message;
        private final Object rejectedValue;

    }
}