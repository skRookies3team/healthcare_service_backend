package com.petlog.healthcare.config;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.collection.*;
import io.milvus.param.index.CreateIndexParam;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Milvus 초기화 클래스
 * * - MilvusProperties에서 컬렉션 이름 및 차원 정보를 가져옵니다.
 * - 애플리케이션 시작 시 컬렉션 존재 여부를 확인하고, 없으면 생성 및 인덱싱을 수행합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MilvusInitializer {

    private final MilvusServiceClient milvusClient;
    private final MilvusProperties milvusProperties;

    @PostConstruct
    public void initialize() {
        String collectionName = milvusProperties.getCollectionName();
        int dimension = milvusProperties.getEmbeddingDimension();

        try {
            log.info("═══════════════════════════════════════");
            log.info("🚀 Milvus 초기화 시작");
            log.info("   Collection: {}", collectionName);
            log.info("   Dimension: {} (Titan Embeddings)", dimension);
            log.info("═══════════════════════════════════════");

            // 1. 컬렉션 존재 여부 확인
            if (hasCollection(collectionName)) {
                log.info("✅ 컬렉션이 이미 존재합니다: {}", collectionName);
                loadCollection(collectionName);
                return;
            }

            // 2. 컬렉션 생성
            createCollection(collectionName, dimension);

            // 3. 인덱스 생성
            createIndex(collectionName);

            // 4. 컬렉션 로드
            loadCollection(collectionName);

            log.info("═══════════════════════════════════════");
            log.info("✅ Milvus 초기화 완료!");
            log.info("═══════════════════════════════════════");

        } catch (Exception e) {
            log.error("❌ Milvus 초기화 실패", e);
            throw new RuntimeException("Milvus 초기화 실패", e);
        }
    }

    private boolean hasCollection(String collectionName) {
        HasCollectionParam param = HasCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();

        return milvusClient.hasCollection(param).getData();
    }

    private void createCollection(String collectionName, int dimension) {
        log.info("📝 컬렉션 생성 중...");

        // 기본 PK 필드 (자동 생성 ID)
        FieldType idField = FieldType.newBuilder()
                .withName("id")
                .withDataType(DataType.Int64)
                .withPrimaryKey(true)
                .withAutoID(true)
                .build();

        // 일기 ID 필드
        FieldType diaryIdField = FieldType.newBuilder()
                .withName("diary_id")
                .withDataType(DataType.Int64)
                .build();

        // 사용자 ID 필드
        FieldType userIdField = FieldType.newBuilder()
                .withName("user_id")
                .withDataType(DataType.Int64)
                .build();

        // 반려동물 ID 필드
        FieldType petIdField = FieldType.newBuilder()
                .withName("pet_id")
                .withDataType(DataType.Int64)
                .build();

        // 원문 내용 필드 (최대 65535자)
        FieldType contentField = FieldType.newBuilder()
                .withName("content")
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();

        // 벡터 필드 (Titan 1024차원)
        FieldType embeddingField = FieldType.newBuilder()
                .withName("embedding")
                .withDataType(DataType.FloatVector)
                .withDimension(dimension)
                .build();

        // 컬렉션 생성 파라미터 구성
        CreateCollectionParam param = CreateCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .withDescription("Healthcare Service - Diary Vectors (Titan 1024D)")
                .withFieldTypes(Arrays.asList(
                        idField, diaryIdField, userIdField, petIdField,
                        contentField, embeddingField
                ))
                .build();

        milvusClient.createCollection(param);
        log.info("✅ 컬렉션 생성 완료");
    }

    private void createIndex(String collectionName) {
        log.info("🔍 인덱스 생성 중...");

        // IVF_FLAT 인덱스 및 COSINE 유사도 측정 방식 설정
        CreateIndexParam param = CreateIndexParam.newBuilder()
                .withCollectionName(collectionName)
                .withFieldName("embedding")
                .withIndexType(IndexType.IVF_FLAT)
                .withMetricType(MetricType.COSINE)
                .withExtraParam("{\"nlist\":128}")
                .withSyncMode(Boolean.TRUE)
                .build();

        milvusClient.createIndex(param);
        log.info("✅ 인덱스 생성 완료");
    }

    private void loadCollection(String collectionName) {
        log.info("💾 컬렉션 로드 중...");

        LoadCollectionParam param = LoadCollectionParam.newBuilder()
                .withCollectionName(collectionName)
                .build();

        milvusClient.loadCollection(param);
        log.info("✅ 컬렉션 로드 완료");
    }
}