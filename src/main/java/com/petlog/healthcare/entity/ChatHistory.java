package com.petlog.healthcare.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Chat History Entity
 * 사용자와 AI 챗봇 간의 대화 기록 저장
 *
 * WHY?
 * - 대화 컨텍스트 유지
 * - 사용자 피드백 분석
 * - 응답 품질 개선
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@Entity
@Table(name = "chat_histories", indexes = {
        @Index(name = "idx_user_pet", columnList = "userId,petId"),
        @Index(name = "idx_created_at", columnList = "createdAt")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChatHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자 ID
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 반려동물 ID
     */
    @Column(nullable = false)
    private Long petId;

    /**
     * 채팅 타입
     * - GENERAL: 일반 챗봇 (수의사 모드)
     * - PERSONA: 페르소나 챗봇 (반려동물 시점)
     * - QUICK: 빠른 팁 (Haiku)
     */
    @Column(nullable = false, length = 20)
    private String chatType;

    /**
     * 사용자 메시지
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String userMessage;

    /**
     * 봇 응답
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String botResponse;

    /**
     * 응답 생성 시간 (ms)
     */
    @Column
    private Integer responseTimeMs;

    /**
     * 생성 일시
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 사용자 피드백 (좋아요/싫어요)
     * null: 피드백 없음, true: 좋아요, false: 싫어요
     */
    @Column
    private Boolean userFeedback;

    @Builder
    public ChatHistory(Long userId, Long petId, String chatType,
                       String userMessage, String botResponse,
                       Integer responseTimeMs, LocalDateTime createdAt) {
        validateInput(userId, petId, chatType, userMessage, botResponse);

        this.userId = userId;
        this.petId = petId;
        this.chatType = chatType;
        this.userMessage = userMessage;
        this.botResponse = botResponse;
        this.responseTimeMs = responseTimeMs;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
    }

    /**
     * 사용자 피드백 설정
     */
    public void setFeedback(Boolean feedback) {
        this.userFeedback = feedback;
    }

    // Validation
    private static void validateInput(Long userId, Long petId, String chatType,
                                      String userMessage, String botResponse) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 필수이며 양수여야 합니다");
        }
        if (petId == null || petId <= 0) {
            throw new IllegalArgumentException("반려동물 ID는 필수이며 양수여야 합니다");
        }
        if (chatType == null || chatType.isBlank()) {
            throw new IllegalArgumentException("채팅 타입은 필수입니다");
        }
        if (userMessage == null || userMessage.isBlank()) {
            throw new IllegalArgumentException("사용자 메시지는 비어있을 수 없습니다");
        }
        if (botResponse == null || botResponse.isBlank()) {
            throw new IllegalArgumentException("봇 응답은 비어있을 수 없습니다");
        }
    }
}