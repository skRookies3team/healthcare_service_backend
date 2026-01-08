package com.petlog.healthcare.domain.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Diary Memory Entity
 * Kafka로부터 받은 일기를 벡터화하여 Milvus에 저장
 *
 * WHY? Persona Chat에서 RAG (Retrieval-Augmented Generation) 수행 시
 * 사용자의 반려동물 관련 일기를 유사도 기반으로 검색하기 위함
 */
@Entity
@Table(name = "diary_memories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiaryMemory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Diary Service에서 보낸 일기 ID
     * Milvus에는 벡터만 저장되므로 원본 일기 ID 필요
     */
    @Column(nullable = false)
    private Long diaryId;

    /**
     * 사용자 ID (파티셔닝 키)
     */
    @Column(nullable = false)
    private Long userId;

    /**
     * 반려동물 ID (필터링 키)
     */
    @Column(nullable = false)
    private Long petId;

    /**
     * 일기 원본 텍스트
     * 검색 결과로 반환할 때 참고용
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /**
     * Titan Embeddings로 생성된 벡터
     * 1024 차원 (Titan Embeddings v2)
     */
    @Column(columnDefinition = "BYTEA")
    private byte[] vectorEmbedding;

    /**
     * 일기 생성 일시
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 벡터 저장 완료 일시
     */
    @Column
    private LocalDateTime vectorizedAt;

    @Builder
    public DiaryMemory(Long diaryId, Long userId, Long petId, String content,
                       byte[] vectorEmbedding) {
        // WHY? Setter 없음 - Rich Domain Model 패턴
        // 생성 후 불변성 보장
        validateInput(diaryId, userId, petId, content);

        this.diaryId = diaryId;
        this.userId = userId;
        this.petId = petId;
        this.content = content;
        this.vectorEmbedding = vectorEmbedding;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 벡터화 완료 표시
     * Kafka Consumer에서 호출
     */
    public void markAsVectorized() {
        this.vectorizedAt = LocalDateTime.now();
    }

    // Validation
    private static void validateInput(Long diaryId, Long userId, Long petId, String content) {
        if (diaryId == null || diaryId <= 0) {
            throw new IllegalArgumentException("일기 ID는 필수이며 양수여야 합니다");
        }
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 필수이며 양수여야 합니다");
        }
        if (petId == null || petId <= 0) {
            throw new IllegalArgumentException("반려동물 ID는 필수이며 양수여야 합니다");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("일기 내용은 비어있을 수 없습니다");
        }
    }
}
