package com.petlog.healthcare.service;

import com.petlog.healthcare.infrastructure.bedrock.TitanEmbeddingClient;
import com.petlog.healthcare.infrastructure.milvus.MilvusSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Hybrid RAG Service (라이펫 문서 + Diary 벡터)
 *
 * [검색 전략]
 * 1. 라이펫 문서 (SimpleFileRagService): 일반 건강 정보
 * 2. Diary 벡터 (Milvus): 해당 반려동물의 과거 기록
 * 3. 두 결과를 병합하여 Claude에게 전달
 *
 * @author healthcare-team
 * @since 2025-01-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRagService {

    private final SimpleFileRagService simpleFileRagService;
    private final TitanEmbeddingClient titanEmbeddingClient;
    private final MilvusSearchService milvusSearchService;

    /**
     * 하이브리드 RAG 검색
     *
     * @param query 사용자 질문
     * @param petId 반려동물 ID (Diary 필터링용)
     * @return RAG 컨텍스트
     */
    public String search(String query, Long petId) {
        log.info("═══════════════════════════════════════");
        log.info("🔍 Hybrid RAG 검색 시작");
        log.info("   질문: '{}'", query);
        log.info("   Pet ID: {}", petId);
        log.info("═══════════════════════════════════════");

        try {
            // ========================================
            // Step 1: 라이펫 문서 검색 (일반 건강 정보)
            // ========================================
            String lifetContext = simpleFileRagService.search(query);
            log.info("✅ 라이펫 문서 검색 완료: {}자", lifetContext.length());

            // ========================================
            // Step 2: Diary 벡터 검색 (과거 기록)
            // ========================================
            String diaryContext = "";
            if (petId != null) {
                try {
                    // 2-1. 질문을 벡터로 변환
                    float[] queryEmbedding = titanEmbeddingClient.generateEmbedding(query);

                    // 2-2. Milvus 유사도 검색
                    List<MilvusSearchService.SearchResult> diaryResults =
                            milvusSearchService.search(queryEmbedding, petId, 3);

                    // 2-3. 결과 포맷팅
                    if (!diaryResults.isEmpty()) {
                        diaryContext = formatDiaryResults(diaryResults);
                        log.info("✅ Diary 벡터 검색 완료: {}개 결과", diaryResults.size());
                    }

                } catch (Exception e) {
                    log.warn("⚠️ Diary 검색 실패 (라이펫 문서만 사용): {}", e.getMessage());
                }
            }

            // ========================================
            // Step 3: 결과 병합
            // ========================================
            String finalContext = buildFinalContext(lifetContext, diaryContext);

            log.info("═══════════════════════════════════════");
            log.info("✅ 검색 완료: 총 {}자", finalContext.length());
            log.info("═══════════════════════════════════════");

            return finalContext;

        } catch (Exception e) {
            log.error("❌ RAG 검색 실패", e);
            return "관련 자료를 찾을 수 없습니다.";
        }
    }

    /**
     * Diary 검색 결과 포맷팅
     */
    private String formatDiaryResults(List<MilvusSearchService.SearchResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n\n### 🐾 과거 일기 기록:\n\n");

        for (int i = 0; i < results.size(); i++) {
            MilvusSearchService.SearchResult result = results.get(i);
            sb.append(String.format("[%d] %s (유사도: %.0f%%)\n",
                    i + 1,
                    result.getContent(),
                    result.getScore() * 100));
            sb.append(String.format("   날짜: %s\n\n", result.getCreatedAt()));
        }

        return sb.toString();
    }

    /**
     * 최종 컨텍스트 구성
     */
    private String buildFinalContext(String lifetContext, String diaryContext) {
        StringBuilder sb = new StringBuilder();

        // 라이펫 문서
        if (lifetContext != null && !lifetContext.isEmpty()) {
            sb.append("### 📚 라이펫 건강 정보:\n\n");
            sb.append(lifetContext);
        }

        // Diary 기록
        if (diaryContext != null && !diaryContext.isEmpty()) {
            sb.append(diaryContext);
        }

        return sb.toString();
    }
}