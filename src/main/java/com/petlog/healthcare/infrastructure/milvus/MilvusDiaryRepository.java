package com.petlog.healthcare.infrastructure.milvus;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.response.MutationResultWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.*;

/**
 * Milvus Diary Repository (직접 저장)
 *
 * [WHY 직접 저장?]
 * Spring AI VectorStore는 내부 EmbeddingModel을 사용하므로
 * Titan으로 생성한 벡터를 직접 넣을 수 없습니다.
 * 따라서 Milvus SDK를 직접 사용하여 벡터를 저장합니다.
 *
 * @author healthcare-team
 * @since 2025-01-02
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MilvusDiaryRepository {

    private final MilvusServiceClient milvusClient;

    private static final String COLLECTION_NAME = "diary_vectors";

    /**
     * Diary 벡터 저장
     *
     * @param diaryId Diary ID
     * @param embedding 1024차원 벡터 (Titan Embeddings)
     * @param metadata 메타데이터 (userId, petId, content 등)
     */
    public void insert(Long diaryId, float[] embedding, Map<String, Object> metadata) {
        try {
            // 필드 데이터 준비
            List<Long> ids = Collections.singletonList(diaryId);
            List<List<Float>> embeddings = Collections.singletonList(toList(embedding));
            List<String> contents = Collections.singletonList((String) metadata.get("content"));
            List<String> metadataJsons = Collections.singletonList(toJson(metadata));

            // Insert 파라미터 구성
            InsertParam insertParam = InsertParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withFields(Arrays.asList(
                            new InsertParam.Field("id", ids),
                            new InsertParam.Field("embedding", embeddings),
                            new InsertParam.Field("content", contents),
                            new InsertParam.Field("metadata", metadataJsons)
                    ))
                    .build();

            // 저장 실행
            MutationResult result = milvusClient.insert(insertParam).getData();
            MutationResultWrapper wrapper = new MutationResultWrapper(result);

            log.info("✅ Milvus 저장 완료 - diaryId: {}, insertCount: {}",
                    diaryId, wrapper.getInsertCount());

        } catch (Exception e) {
            log.error("❌ Milvus 저장 실패 - diaryId: {}", diaryId, e);
            throw new RuntimeException("Milvus 저장 실패", e);
        }
    }

    /**
     * Diary 벡터 삭제
     *
     * @param diaryId Diary ID
     */
    public void delete(Long diaryId) {
        try {
            // 삭제 조건 (id == diaryId)
            String expr = String.format("id == %d", diaryId);

            DeleteParam deleteParam = DeleteParam.newBuilder()
                    .withCollectionName(COLLECTION_NAME)
                    .withExpr(expr)
                    .build();

            milvusClient.delete(deleteParam);

            log.info("✅ Milvus 삭제 완료 - diaryId: {}", diaryId);

        } catch (Exception e) {
            log.error("❌ Milvus 삭제 실패 - diaryId: {}", diaryId, e);
            throw new RuntimeException("Milvus 삭제 실패", e);
        }
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
     * Map → JSON String 변환
     */
    private String toJson(Map<String, Object> map) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}