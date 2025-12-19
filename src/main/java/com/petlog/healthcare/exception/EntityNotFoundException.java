package com.petlog.healthcare.exception;

import lombok.Getter;

@Getter
public class EntityNotFoundException extends BusinessException {
    public EntityNotFoundException(String entityName, Object identifier) {
        super(ErrorCode.valueOf(entityName.toUpperCase() + "_NOT_FOUND"),
                String.format("%s를 찾을 수 없습니다. ID: %s", entityName, identifier));
    }

    // ErrorCode를 직접 넘기는 생성자 (DiaryService에서 사용)
    public EntityNotFoundException(ErrorCode errorCode) {
        super(errorCode, errorCode.getMessage());
    }
}