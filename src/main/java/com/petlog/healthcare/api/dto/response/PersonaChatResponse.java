package com.petlog.healthcare.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Persona Chat Response DTO
 * Persona 챗봇이 반환하는 응답
 *
 * WHY? RAG를 통해 관련된 일기와 건강 기록도 함께 제공
 * 사용자에게 더 개인화된 답변 가능
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonaChatResponse {

    /**
     * AI 봇의 응답 메시지 (Claude Sonnet 생성)
     */
    @JsonProperty("answer")
    private String answer;

    /**
     * 응답 생성에 사용된 관련 일기 ID 목록
     * RAG를 통해 검색된 상위 3개의 관련 일기
     */
    @JsonProperty("relatedDiaries")
    private List<Long> relatedDiaries;

    /**
     * 응답 생성 타임스탬프
     */
    @JsonProperty("timestamp")
    private LocalDateTime timestamp;

    /**
     * Factory Method - 응답 객체 생성
     *
     * @param answer 봇 응답
     * @param relatedDiaries 관련 일기 ID 목록
     * @return PersonaChatResponse 인스턴스
     */
    public static PersonaChatResponse of(String answer, List<Long> relatedDiaries) {
        return PersonaChatResponse.builder()
                .answer(answer)
                .relatedDiaries(relatedDiaries)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
