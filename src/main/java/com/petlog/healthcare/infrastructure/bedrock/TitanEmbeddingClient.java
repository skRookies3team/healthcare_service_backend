package com.petlog.healthcare.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petlog.healthcare.config.BedrockConfig.BedrockProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * AWS Bedrock Titan Embeddings Client
 *
 * [핵심 기능]
 * 텍스트를 벡터(Embeddings)로 변환하여 Milvus Vector DB에 저장
 *
 * [WHY Titan Embeddings?]
 * 1. **비용 최적화**
 *    - OpenAI: $0.00013/1K tokens (text-embedding-3-small)
 *    - Titan: $0.0001/1K tokens (약 30% 저렴)
 *
 * 2. **MSA 표준 통합**
 *    - Claude (Bedrock), Titan (Bedrock), S3 → 모두 AWS 생태계
 *    - 단일 인증 체계 (Bearer Token)
 *    - 리전 일치 (ap-northeast-2)
 *
 * 3. **성능**
 *    - 지연시간: ~100ms (OpenAI와 유사)
 *    - 벡터 차원: 1024 (OpenAI 1536보다 작아 저장 효율적)
 *
 * [모델 정보]
 * - Model ID: amazon.titan-embed-text-v2:0
 * - Vector Dimension: 1024
 * - Max Input Tokens: 8192 (일기 내용 충분히 커버)
 * - Region: ap-northeast-2 (한국)
 *
 * [인증 방식]
 * Authorization: Bearer {API_KEY} (ClaudeClient와 동일)
 *
 * @author healthcare-team
 * @since 2025-01-02
 * @version 1.0
 *
 * Issue: #healthcare-titan-embeddings
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TitanEmbeddingClient {

    private final ObjectMapper objectMapper;
    private final BedrockProperties bedrockProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    // Titan Embeddings Model ID
    private static final String TITAN_MODEL_ID = "amazon.titan-embed-text-v2:0";

    // 벡터 차원 (Milvus 설정과 일치해야 함)
    private static final int EMBEDDING_DIMENSION = 1024;

    /**
     * 텍스트를 벡터로 변환 (동기 호출)
     *
     * [처리 흐름]
     * 1. Request Body 생성 (JSON)
     * 2. Bedrock API 호출 (POST /model/amazon.titan-embed-text-v2:0/invoke)
     * 3. 응답 파싱 (embedding 배열 추출)
     * 4. float[] 반환
     *
     * [에러 처리]
     * - 401 Unauthorized: API 키 확인
     * - 404 Not Found: 모델 ID 또는 리전 확인
     * - 429 Too Many Requests: Rate Limit (재시도 로직 필요)
     *
     * @param text 벡터화할 텍스트 (일기 내용)
     * @return 1024차원 벡터 (float[])
     * @throws RuntimeException 벡터화 실패 시
     */
    public float[] generateEmbedding(String text) {
        log.info("🔄 Titan Embeddings 생성 시작");
        log.debug("   텍스트 길이: {}자", text.length());

        try {
            // ========================================
            // Step 1: API 엔드포인트 구성
            // ========================================
            String endpoint = String.format(
                    "https://bedrock-runtime.%s.amazonaws.com/model/%s/invoke",
                    bedrockProperties.getRegion(),
                    TITAN_MODEL_ID
            );
            log.debug("📍 Endpoint: {}", endpoint);

            // ========================================
            // Step 2: Request Body 생성
            // ========================================
            String requestBody = buildTitanRequestBody(text);
            log.debug("📤 Request body length: {} characters", requestBody.length());

            // ========================================
            // Step 3: HTTP 요청 생성
            // ========================================
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + bedrockProperties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            log.info("🚀 Bedrock API 호출 중...");

            // ========================================
            // Step 4: HTTP 요청 실행
            // ========================================
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // ========================================
            // Step 5: 응답 상태 확인
            // ========================================
            log.info("📥 Response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                log.error("❌ Titan Embeddings API 호출 실패");
                log.error("   Status: {}", response.statusCode());
                log.error("   Model: {}", TITAN_MODEL_ID);
                log.error("   Error Body: {}", errorBody);

                handleErrorResponse(response.statusCode());
            }

            // ========================================
            // Step 6: 응답 파싱
            // ========================================
            String responseBody = response.body();
            float[] embedding = parseTitanResponse(responseBody);

            log.info("✅ Titan Embeddings 생성 완료 - 차원: {}", embedding.length);
            return embedding;

        } catch (RuntimeException e) {
            throw e; // 이미 처리된 예외는 그대로 전달
        } catch (Exception e) {
            log.error("❌ Titan Embeddings 생성 실패", e);
            throw new RuntimeException("Titan Embeddings 생성 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * Titan Embeddings Request Body 생성
     *
     * [Titan API 형식]
     * {
     *   "inputText": "텍스트 내용",
     *   "dimensions": 1024,
     *   "normalize": true
     * }
     *
     * @param text 벡터화할 텍스트
     * @return JSON 문자열
     */
    private String buildTitanRequestBody(String text) {
        try {
            var requestBody = objectMapper.createObjectNode();
            requestBody.put("inputText", text);
            requestBody.put("dimensions", EMBEDDING_DIMENSION);
            requestBody.put("normalize", true); // 정규화 (유사도 계산 최적화)

            String result = objectMapper.writeValueAsString(requestBody);
            log.debug("✅ Request body 생성 완료");
            return result;

        } catch (Exception e) {
            log.error("❌ Request body 생성 실패", e);
            throw new RuntimeException("Request body 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Titan Embeddings 응답 파싱
     *
     * [Titan API 응답 형식]
     * {
     *   "embedding": [0.123, -0.456, ...],  // 1024개 float
     *   "inputTextTokenCount": 50
     * }
     *
     * @param responseBody Titan API 응답 JSON
     * @return 1024차원 벡터 (float[])
     */
    private float[] parseTitanResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // embedding 배열 추출
            JsonNode embeddingNode = root.path("embedding");

            if (!embeddingNode.isArray()) {
                log.error("⚠️ 응답에 embedding 배열이 없습니다");
                throw new RuntimeException("응답 형식 오류: embedding 배열 없음");
            }

            // JsonNode → float[] 변환
            int size = embeddingNode.size();
            float[] embedding = new float[size];

            for (int i = 0; i < size; i++) {
                embedding[i] = (float) embeddingNode.get(i).asDouble();
            }

            // 토큰 사용량 로깅 (비용 추적용)
            int tokenCount = root.path("inputTextTokenCount").asInt();
            log.info("📊 Token usage (Titan) - Input: {} tokens", tokenCount);

            // 비용 계산 (참고용)
            double cost = (tokenCount / 1000.0) * 0.0001; // $0.0001/1K tokens
            log.debug("   예상 비용: ${}", String.format("%.6f", cost));

            log.info("✅ Embedding 파싱 완료 - 차원: {}", size);
            return embedding;

        } catch (Exception e) {
            log.error("❌ 응답 파싱 실패: {}", responseBody, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * HTTP 에러 응답 처리
     *
     * @param statusCode HTTP 상태 코드
     */
    private void handleErrorResponse(int statusCode) {
        switch (statusCode) {
            case 401:
                throw new RuntimeException("인증 실패: API 키를 확인해주세요. (401 Unauthorized)");
            case 403:
                throw new RuntimeException("접근 거부: API 키 권한 또는 리전(ap-northeast-2) 설정을 확인해주세요. (403 Forbidden)");
            case 404:
                throw new RuntimeException("모델을 찾을 수 없습니다: 모델 ID(amazon.titan-embed-text-v2:0) 또는 리전을 확인해주세요. (404 Not Found)");
            case 429:
                throw new RuntimeException("Rate Limit 초과: 잠시 후 다시 시도해주세요. (429 Too Many Requests)");
            default:
                throw new RuntimeException("Titan Embeddings API 호출 실패: " + statusCode);
        }
    }

    /**
     * 벡터 차원 반환 (Milvus 설정용)
     *
     * @return 1024
     */
    public static int getEmbeddingDimension() {
        return EMBEDDING_DIMENSION;
    }
}