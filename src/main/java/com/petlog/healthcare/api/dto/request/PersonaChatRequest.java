package com.petlog.healthcare.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persona Chat Request DTO
 * 사용자가 Persona 챗봇에 보내는 요청
 *
 * WHY? DTO는 외부 요청과 내부 서비스 계층 사이의 경계
 * 요청 검증(Validation)을 한곳에서 수행
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaChatRequest {

    /**
     * 사용자 ID - Member Service에서 받은 ID
     */
    @NotNull(message = "사용자 ID는 필수입니다")
    private Long userId;

    /**
     * 반려동물 ID - Pet 고유 식별자
     */
    @NotNull(message = "반려동물 ID는 필수입니다")
    private Long petId;

    /**
     * 사용자 메시지 - 질문이나 대화 내용
     */
    @NotBlank(message = "메시지는 비어있을 수 없습니다")
    private String message;
}
