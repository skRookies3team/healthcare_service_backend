package com.petlog.healthcare.service;

import com.petlog.healthcare.dto.event.DiaryEventMessage;
import com.petlog.healthcare.infrastructure.bedrock.TitanEmbeddingClient;
import com.petlog.healthcare.infrastructure.milvus.MilvusDiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Diary Vector Service (Titan Embeddings 사용)
 *
 * [핵심 변경]
 * - OpenAI Embeddings → AWS Titan Embeddings (1024차원)
 * - 비용 절감: $0.00013 → $0.0001 per 1K tokens
 *
 * [처리 흐름]
 * 1. Kafka로 Diary 이벤트 수신 (DiaryEventConsumer)
 * 2. Titan Embeddings로 벡터화 (1024차원)
 * 3. Milvus Vector DB에 저장 (메타데이터 포함)
 * 4. RAG 검색 시 유사도 기반으로 조회
 *
 * @author healthcare-team
 * @since 2025-01-02
 * @version 3.0 (Titan Embeddings 통합)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryVectorService {

    private final TitanEmbeddingClient titanEmbeddingClient;
    private final MilvusDiaryRepository milvusDiaryRepository;
    /**
     * Diary 벡터화 및 저장 (Titan Embeddings 사용)
     *
     * [처리 로직]
     * 1. 텍스트 전처리 (공백 정리)
     * 2. Titan Embeddings API 호출 → 1024차원 벡터 생성
     * 3. 메타데이터 구성 (diaryId, userId, petId, createdAt)
     * 4. Milvus Vector DB에 저장
     *
     * [메타데이터 활용]
     * - userId, petId: RAG 검색 시 필터링
     * - diaryId: 삭제 시 식별자
     * - createdAt: 최신순 정렬
     *
     * @param diaryId Diary ID
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param content Diary 내용
     * @param imageUrl 이미지 URL (참조용)
     * @param createdAt 생성 시간
     */
    @Transactional
    public void vectorizeAndStore(
            Long diaryId,
            Long userId,
            Long petId,
            String content,
            String imageUrl,
            LocalDateTime createdAt
    ) {
        log.info("🔄 벡터화 시작 - diaryId: {}", diaryId);

        try {
            String cleanedContent = preprocessText(content);
            if (cleanedContent.isBlank()) return;

            // Step 1: Titan Embeddings 생성
            float[] embedding = titanEmbeddingClient.generateEmbedding(cleanedContent);

            // Step 2: 메타데이터 구성
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("diaryId", diaryId.toString());
            metadata.put("userId", userId.toString());
            metadata.put("petId", petId.toString());
            metadata.put("content", cleanedContent); // ✅ 내용도 저장
            if (imageUrl != null) metadata.put("imageUrl", imageUrl);
            if (createdAt != null) metadata.put("createdAt", createdAt.toString());

            // Step 3: Milvus 직접 저장
            milvusDiaryRepository.insert(diaryId, embedding, metadata);

            log.info("✅ 벡터화 완료 - diaryId: {}", diaryId);

        } catch (Exception e) {
            log.error("❌ 벡터화 실패 - diaryId: {}", diaryId, e);
        }
    }

    /**
     * 벡터 삭제
     *
     * @param diaryId Diary ID
     */
    @Transactional
    public void deleteVector(Long diaryId) {
        try {
            milvusDiaryRepository.delete(diaryId);
        } catch (Exception e) {
            log.error("❌ 벡터 삭제 실패 - diaryId: {}", diaryId, e);
        }
    }

    // DiaryVectorService.java 내부에 추가할 권장 메서드
    @Transactional
    public void vectorizeAndStore(DiaryEventMessage message) {
        this.vectorizeAndStore(
                message.getDiaryId(),
                message.getUserId(),
                message.getPetId(),
                message.getContent(),
                message.getImageUrl(),
                message.getCreatedAt()
        );
    }

    @Transactional
    public void updateVector(DiaryEventMessage message) {
        this.deleteVector(message.getDiaryId());
        this.vectorizeAndStore(message);
    }
    /**
     * 텍스트 전처리
     *
     * @param text 원본 텍스트
     * @return 전처리된 텍스트
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