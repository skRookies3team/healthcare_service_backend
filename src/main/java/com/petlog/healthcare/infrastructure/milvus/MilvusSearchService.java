package com.petlog.healthcare.infrastructure.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Milvus 검색 서비스 (유사도 검색)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusSearchService {

    private final MilvusServiceClient milvusClient;

    private static final String COLLECTION_NAME = "diary_vectors";
    private static final int NPROBE = 10;

    public List<SearchResult> search(float[] queryEmbedding, Long petId, int topK) {
        // [해결] 리스트를 먼저 선언해야 에러가 나지 않습니다.
        List<SearchResult> searchResults = new ArrayList<>();

        try {
            // 필터 조건: petId는 Long이므로 숫자로 처리
            String expr = String.format("petId == %d", petId);

            SearchParam searchParam = SearchParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withMetricType(io.milvus.param.MetricType.COSINE)
                    .withOutFields(List.of("content", "metadata"))
                    .withTopK(topK)
                    .withVectors(Collections.singletonList(toList(queryEmbedding)))
                    .withVectorFieldName("embedding")
                    .withExpr(expr)
                    .withParams("{\"nprobe\":" + NPROBE + "}")
                    .build();

            SearchResults results = milvusClient.search(searchParam).getData();
            SearchResultsWrapper wrapper = new SearchResultsWrapper(results.getResults());

            List<SearchResultsWrapper.IDScore> idScores = wrapper.getIDScore(0);

            for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
                SearchResultsWrapper.IDScore idScore = idScores.get(i);

                String content = (String) wrapper.getFieldData("content", 0).get(i);
                String metadata = (String) wrapper.getFieldData("metadata", 0).get(i);

                // [해결] 아래에 정의된 parseCreatedAt을 호출합니다.
                String createdAt = parseCreatedAt(metadata);

                // [해결] 생성자 파라미터 순서: Long, String, float, String 순서로 정확히 맞춥니다.
                searchResults.add(new SearchResult(
                        idScore.getLongID(), // Long
                        content,             // String (내용)
                        idScore.getScore(),  // float (점수)
                        createdAt            // String (날짜)
                ));
            }

            log.info("✅ Milvus 검색 완료 - petId: {}, 결과: {}개", petId, searchResults.size());
            return searchResults;

        } catch (Exception e) {
            log.error("❌ Milvus 검색 실패 - petId: {}", petId, e);
            return Collections.emptyList();
        }
    }

    private List<Float> toList(float[] array) {
        List<Float> list = new ArrayList<>(array.length);
        for (float v : array) {
            list.add(v);
        }
        return list;
    }

    // [해결] 메서드가 클래스 내부에 정확히 위치하도록 합니다.
    private String parseCreatedAt(String metadata) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(metadata);
            return node.path("createdAt").asText("Unknown");
        } catch (Exception e) {
            return "Unknown";
        }
    }

    /**
     * 검색 결과 DTO
     */
    @Getter
    @AllArgsConstructor // [해결] final 필드를 위한 생성자를 자동으로 생성합니다.
    public static class SearchResult {
        private final Long diaryId;
        private final String content;
        private final float score;
        private final String createdAt;
    }
}