package com.petlog.healthcare.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

@Slf4j
@Component
@RequiredArgsConstructor
public class TitanEmbeddingClient {

    private final BedrockRuntimeClient bedrockClient;
    private final ObjectMapper objectMapper;

    /**
     * Titan Embeddings v2 (1024차원, ADR-003 준수)
     */
    public float[] generateEmbedding(String text) {
        log.debug("Titan Embeddings 생성: 길이={}", text.length());

        try {
            String payload = "{\"inputText\": \"" + text.replace("\"", "\\\"") + "\"}";

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId("amazon.titan-embed-text-v2:0")
                    .body(SdkBytes.fromUtf8String(payload))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);
            String result = response.body().asUtf8String();

            return parseEmbedding(result);

        } catch (Exception e) {
            log.error("Titan Embeddings 실패: {}", e.getMessage());
            throw new RuntimeException("임베딩 생성 실패", e);
        }
    }

    private float[] parseEmbedding(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode embeddingNode = root.get("embedding");

        float[] vector = new float[1024];  // Titan v2: 1024차원
        for (int i = 0; i < 1024; i++) {
            vector[i] = embeddingNode.get(i).floatValue();
        }
        return vector;
    }
}
