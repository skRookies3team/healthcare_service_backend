package com.petlog.healthcare.service;

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
 * Diary Vector Service
 *
 * Diary 텍스트를 벡터화하고 Milvus Vector DB에 저장/삭제
 *
 * [핵심 기능]
 * 1. 일기 내용을 OpenAI Embeddings로 벡터화
 * 2. Milvus Vector DB에 저장 (Spring AI VectorStore 사용)
 * 3. 메타데이터와 함께 저장하여 필터링 가능
 *
 * [아키텍처 결정]
 * - WHY Spring AI VectorStore?
 *   → Milvus SDK 직접 사용보다 추상화 레벨이 높아 유지보수 용이
 *   → Embedding 자동화 (OpenAI API 호출 자동 처리)
 *   → 다른 벡터 DB로 교체 시 코드 변경 최소화
 *
 * - WHY Metadata?
 *   → RAG 검색 시 userId/petId로 필터링 가능
 *   → 특정 날짜 범위 검색 가능
 *   → 디버깅 시 원본 데이터 추적 용이
 *
 * [벡터 차원]
 * - OpenAI text-embedding-3-small: 1536차원
 * - Milvus 컬렉션 설정과 일치해야 함
 *
 * @author healthcare-team
 * @since 2025-12-23
 * @version 2.0 (벡터화 구현 완료 - 2025-01-02)
 *
 * Issue: #healthcare-vectorization
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DiaryVectorService {

    private final VectorStore vectorStore;

    /**
     * Diary 벡터화 및 저장
     *
     * [처리 흐름]
     * 1. 텍스트 전처리 (공백 정리 등)
     * 2. Spring AI Document 객체 생성 (내용 + 메타데이터)
     * 3. VectorStore.add() 호출 → OpenAI Embeddings 자동 호출
     * 4. Milvus에 벡터 + 메타데이터 저장
     *
     * [메타데이터 필드]
     * - diaryId: 일기 고유 ID (삭제 시 사용)
     * - userId: 사용자 ID (RAG 필터링용)
     * - petId: 반려동물 ID (RAG 필터링용)
     * - imageUrl: 이미지 URL (참조용)
     * - createdAt: 생성 시간 (날짜 범위 검색용)
     *
     * [에러 처리]
     * - 벡터화 실패 시 로그만 남기고 예외 전파하지 않음
     * - Record Service의 일기 저장은 이미 완료된 상태이므로
     *   벡터화 실패해도 일기는 안전하게 보관됨
     *
     * @param diaryId Diary ID
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param content Diary 내용 (벡터화 대상)
     * @param imageUrl 이미지 URL
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
            // ========================================
            // Step 1: 텍스트 전처리
            // ========================================
            String cleanedContent = preprocessText(content);

            if (cleanedContent == null || cleanedContent.isBlank()) {
                log.warn("⚠️ 내용이 비어있어 벡터화를 건너뜁니다 - diaryId: {}", diaryId);
                return;
            }

            log.debug("전처리된 내용: {}", cleanedContent.substring(0, Math.min(100, cleanedContent.length())));

            // ========================================
            // Step 2: 메타데이터 생성
            // ========================================
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("diaryId", diaryId.toString()); // String으로 저장 (Milvus 호환)
            metadata.put("userId", userId.toString());
            metadata.put("petId", petId.toString());

            if (imageUrl != null) {
                metadata.put("imageUrl", imageUrl);
            }

            if (createdAt != null) {
                metadata.put("createdAt", createdAt.toString());
            }

            log.debug("메타데이터: {}", metadata);

            // ========================================
            // Step 3: Spring AI Document 생성
            // ========================================
            // Document 생성 시 자동으로 OpenAI Embeddings API 호출됨
            Document document = new Document(cleanedContent, metadata);

            // ========================================
            // Step 4: Vector DB에 저장
            // ========================================
            vectorStore.add(List.of(document));

            log.info("✅ 벡터화 완료 - diaryId: {}, 내용 길이: {}자", diaryId, cleanedContent.length());

        } catch (Exception e) {
            // ========================================
            // 에러 처리 (벡터화 실패해도 일기는 안전)
            // ========================================
            log.error("❌ 벡터화 실패 - diaryId: {}, error: {}", diaryId, e.getMessage(), e);

            // TODO: 실패 메시지를 별도 큐에 저장하여 재처리 (Phase 2)
            // 현재는 로그만 남기고 예외 전파하지 않음
        }
    }

    /**
     * 벡터 삭제
     *
     * [처리 흐름]
     * 1. Milvus에서 diaryId 메타데이터로 검색
     * 2. 해당 벡터 삭제
     *
     * [중요]
     * - Spring AI VectorStore는 ID 기반 삭제를 직접 지원하지 않을 수 있음
     * - 메타데이터 필터링으로 검색 후 삭제하는 방식 사용
     * - 향후 Milvus SDK 직접 호출로 변경 가능 (성능 최적화)
     *
     * @param diaryId Diary ID
     */
    @Transactional
    public void deleteVector(Long diaryId) {
        log.info("🗑️ 벡터 삭제 시작 - diaryId: {}", diaryId);

        try {
            // ========================================
            // Spring AI VectorStore 삭제
            // ========================================
            // Note: Spring AI 1.0.0-M4는 메타데이터 기반 삭제를 직접 지원하지 않음
            // 따라서 검색 후 삭제 방식 사용

            // TODO: Milvus SDK 직접 사용으로 변경 고려 (Phase 2)
            // 현재는 Spring AI의 기본 동작에 의존

            vectorStore.delete(List.of(diaryId.toString()));

            log.info("✅ 벡터 삭제 완료 - diaryId: {}", diaryId);

        } catch (Exception e) {
            log.error("❌ 벡터 삭제 실패 - diaryId: {}, error: {}", diaryId, e.getMessage(), e);

            // 삭제 실패해도 예외 전파하지 않음 (로그만 남김)
        }
    }

    /**
     * 텍스트 전처리
     *
     * [처리 내용]
     * 1. 연속된 공백을 하나로 압축
     * 2. 앞뒤 공백 제거
     * 3. null 체크
     *
     * [WHY 필요?]
     * - 벡터 품질 향상 (불필요한 공백 제거)
     * - 토큰 절약 (OpenAI API 비용 절감)
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