package com.petlog.healthcare.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

/**
 * Diary Vector Service
 *
 * Diary 텍스트를 벡터화하고 Milvus Vector DB에 저장
 *
 * @author healthcare-team
 * @since 2025-12-23
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DiaryVectorService {

    // TODO: TitanEmbeddingClient 주입 (다음 단계에서 구현)
    // private final TitanEmbeddingClient titanEmbeddingClient;

    // TODO: MilvusVectorStore 주입 (다음 단계에서 구현)
    // private final MilvusVectorStore milvusVectorStore;

    /**
     * Diary 벡터화 및 저장
     *
     * @param diaryId Diary ID
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param content Diary 내용
     * @param imageUrl 이미지 URL
     * @param createdAt 생성 시간
     */
    public void vectorizeAndStore(
            Long diaryId,
            Long userId,
            Long petId,
            String content,
            String imageUrl,
            LocalDateTime createdAt
    ) {
        log.info("🔄 Starting vectorization for diaryId: {}", diaryId);

        // ========================================
        // Step 1: 텍스트 전처리 (선택)
        // ========================================
        String cleanedContent = preprocessText(content);
        log.debug("Preprocessed content: {}", cleanedContent);

        // ========================================
        // Step 2: Titan Embeddings로 벡터화
        // ========================================
        // TODO: 다음 단계에서 구현
        // float[] vector = titanEmbeddingClient.generateEmbedding(cleanedContent);
        // log.info("✅ Embedding generated - dimension: {}", vector.length);

        // 임시: 로그만 출력
        log.info("✅ [TODO] Embedding generation - content length: {}", cleanedContent.length());

        // ========================================
        // Step 3: Milvus Vector DB에 저장
        // ========================================
        // TODO: 다음 단계에서 구현
        // milvusVectorStore.insert(diaryId, userId, petId, vector, cleanedContent, createdAt);
        // log.info("✅ Vector stored in Milvus");

        // 임시: 로그만 출력
        log.info("✅ [TODO] Vector storage - diaryId: {}, userId: {}, petId: {}",
                diaryId, userId, petId);
    }

    /**
     * 벡터 삭제
     *
     * @param diaryId Diary ID
     */
    public void deleteVector(Long diaryId) {
        log.info("🗑️ Deleting vector for diaryId: {}", diaryId);

        // TODO: 다음 단계에서 구현
        // milvusVectorStore.delete(diaryId);

        // 임시: 로그만 출력
        log.info("✅ [TODO] Vector deletion - diaryId: {}", diaryId);
    }

    /**
     * 텍스트 전처리
     *
     * - 불필요한 공백 제거
     * - 특수문자 정리
     */
    private String preprocessText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        // 연속된 공백을 하나로
        text = text.replaceAll("\\s+", " ");

        // 앞뒤 공백 제거
        text = text.trim();

        return text;
    }
}
