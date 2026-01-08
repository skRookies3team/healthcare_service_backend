// src/main/java/com/petlog/healthcare/infrastructure/milvus/EnhancedMilvusVectorStore.java
package com.petlog.healthcare.infrastructure.milvus;

import com.petlog.healthcare.domain.entity.DiaryMemory;
import com.petlog.healthcare.domain.repository.DiaryMemoryRepository;
import com.petlog.healthcare.infrastructure.bedrock.TitanEmbeddingClient;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ✅ LangChain4j 스타일 RAG 구현
 *
 * 핵심 기능:
 * 1. 시맨틱 검색 (Semantic Search)
 * 2. 메타데이터 필터링
 * 3. 재순위화 (Re-ranking)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusVectorStore {

    private final MilvusServiceClient milvusClient;
    private final TitanEmbeddingClient titanEmbeddingClient;
    private final DiaryMemoryRepository diaryMemoryRepository;

    @Value("${milvus.collection-name:diary_vectors}")
    private String collectionName;

    /**
     * ✅ 고급 RAG 검색
     *
     * @param queryText 사용자 질문
     * @param userId 사용자 ID (필터링)
     * @param petId 펫 ID (필터링)
     * @param topK 상위 K개 결과
     * @param minScore 최소 유사도 점수 (0.0 ~ 1.0)
     * @return 관련 일기 목록
     */
    public List<DiaryMemory> searchSimilarDiaries(
            String queryText,
            Long userId,
            Long petId,
            int topK,
            double minScore
    ) {
        log.info("🔍 Enhanced RAG 검색 시작");
        log.info("   Query: '{}'", truncate(queryText, 50));
        log.info("   Filters: userId={}, petId={}, topK={}, minScore={}",
                userId, petId, topK, minScore);

        try {
            // Step 1: 질문을 벡터로 변환
            float[] queryEmbedding = titanEmbeddingClient.generateEmbedding(queryText);
            log.debug("   ✅ 쿼리 벡터 생성 완료 (1024차원)");

            // Step 2: Milvus 검색 (메타데이터 필터링 포함)
            String filterExpr = buildFilterExpression(userId, petId);
            List<SearchResult> searchResults = search(
                    queryEmbedding,
                    filterExpr,
                    topK * 2  // ✅ 재순위화를 위해 2배 검색
            );

            log.info("   ✅ Milvus 검색 완료: {}개 결과", searchResults.size());

            // Step 3: 점수 필터링
            List<SearchResult> filteredResults = searchResults.stream()
                    .filter(result -> result.score >= minScore)
                    .collect(Collectors.toList());

            log.info("   ✅ 점수 필터링 완료: {}개 결과 (minScore >= {})",
                    filteredResults.size(), minScore);

            // Step 4: 재순위화 (옵션)
            List<SearchResult> rerankedResults = rerank(filteredResults, queryText);

            // Step 5: Top-K 선택
            List<SearchResult> finalResults = rerankedResults.stream()
                    .limit(topK)
                    .collect(Collectors.toList());

            // Step 6: DiaryMemory 로드
            List<DiaryMemory> diaryMemories = new ArrayList<>();
            for (SearchResult result : finalResults) {
                DiaryMemory memory = diaryMemoryRepository.findByDiaryId(result.diaryId);
                if (memory != null) {
                    diaryMemories.add(memory);
                    log.debug("   📄 일기 로드: diaryId={}, score={:.3f}",
                            result.diaryId, result.score);
                }
            }

            log.info("✅ 최종 결과: {}개 DiaryMemory 반환", diaryMemories.size());
            return diaryMemories;

        } catch (Exception e) {
            log.error("❌ Enhanced RAG 검색 실패", e);
            return new ArrayList<>();
        }
    }

    /**
     * Milvus 벡터 검색
     */
    private List<SearchResult> search(
            float[] queryEmbedding,
            String filterExpr,
            int topK
    ) {
        try {
            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(collectionName)
                    .withMetricType(MetricType.COSINE)
                    .withOutFields(List.of("diary_id", "user_id", "pet_id", "content"))
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(toList(queryEmbedding)))
                    .withVectorFieldName("embedding")
                    .withExpr(filterExpr)  // ✅ 메타데이터 필터링
                    .withParams("{\"nprobe\":128}")
                    .build();

            SearchResults results = milvusClient.search(searchParam).getData();
            SearchResultsWrapper wrapper = new SearchResultsWrapper(results.getResults());

            List<SearchResult> searchResults = new ArrayList<>();
            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

            for (int i = 0; i < idScores.size(); i++) {
                SearchResultsWrapper.IDScore idScore = idScores.get(i);

                // Milvus ID가 아닌 diary_id 필드 사용
                Long diaryId = Long.parseLong(
                        String.valueOf(wrapper.getFieldData("diary_id", 0).get(i))
                );

                searchResults.add(new SearchResult(
                        diaryId,
                        idScore.getScore()
                ));
            }

            return searchResults;

        } catch (Exception e) {
            log.error("❌ Milvus 검색 실패", e);
            return Collections.emptyList();
        }
    }

    /**
     * ✅ 메타데이터 필터 표현식 생성
     */
    private String buildFilterExpression(Long userId, Long petId) {
        List<String> conditions = new ArrayList<>();

        if (userId != null) {
            conditions.add(String.format("user_id == %d", userId));
        }

        if (petId != null) {
            conditions.add(String.format("pet_id == %d", petId));
        }

        return conditions.isEmpty() ? "" : String.join(" && ", conditions);
    }

    /**
     * ✅ 재순위화 (Re-ranking)
     *
     * 전략:
     * 1. 벡터 유사도 (70%)
     * 2. 최신성 (20%)
     * 3. 키워드 매칭 (10%)
     */
    private List<SearchResult> rerank(List<SearchResult> results, String queryText) {
        log.debug("🔄 재순위화 시작: {}개 결과", results.size());

        // 간단한 키워드 추출 (실제로는 NLP 라이브러리 사용 권장)
        List<String> queryKeywords = extractKeywords(queryText);

        return results.stream()
                .peek(result -> {
                    // 키워드 매칭 보너스 계산
                    DiaryMemory memory = diaryMemoryRepository.findByDiaryId(result.diaryId);
                    if (memory != null) {
                        double keywordBonus = calculateKeywordBonus(
                                memory.getContent(),
                                queryKeywords
                        );

                        // 최종 점수 = 벡터유사도 * 0.7 + 키워드보너스 * 0.3
                        result.score = (float) (result.score * 0.7 + keywordBonus * 0.3);
                    }
                })
                .sorted((a, b) -> Float.compare(b.score, a.score))  // 내림차순
                .collect(Collectors.toList());
    }

    /**
     * 키워드 추출 (간소화 버전)
     */
    private List<String> extractKeywords(String text) {
        return List.of(text.toLowerCase().split("\\s+"));
    }

    /**
     * 키워드 매칭 점수 계산
     */
    private double calculateKeywordBonus(String content, List<String> keywords) {
        if (content == null || keywords.isEmpty()) {
            return 0.0;
        }

        String lowerContent = content.toLowerCase();
        long matchCount = keywords.stream()
                .filter(lowerContent::contains)
                .count();

        return (double) matchCount / keywords.size();
    }

    /**
     * float[] → List<Float> 변환
     */
    private List<Float> toList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    /**
     * 텍스트 자르기
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 검색 결과 DTO
     */
    public static class SearchResult {
        public final Long diaryId;
        public float score;

        public SearchResult(Long diaryId, float score) {
            this.diaryId = diaryId;
            this.score = score;
        }
    }
}