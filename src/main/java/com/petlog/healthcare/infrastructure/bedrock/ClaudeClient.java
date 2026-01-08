package com.petlog.healthcare.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petlog.healthcare.config.BedrockConfig.BedrockProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * AWS Bedrock Claude Client (AWS SDK 사용)
 * 
 * WHY: AWS Bedrock은 Bearer Token이 아닌 AWS SigV4 서명 방식 사용
 * → AWS SDK BedrockRuntimeClient를 통해 올바르게 인증
 *
 * @author healthcare-team
 * @since 2026-01-08 (AWS SDK 방식으로 수정)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final ObjectMapper objectMapper;
    private final BedrockProperties bedrockProperties;
    private final BedrockRuntimeClient bedrockRuntimeClient;

    /**
     * ✅ 기존 메서드 유지: Sonnet 기본 호출
     */
    public String invokeClaude(String userMessage) {
        log.info("🤖 [기본 Sonnet] invokeClaude() 호출: {}", truncate(userMessage, 100));
        return invokeClaudeSpecific(bedrockProperties.getModelId(), userMessage);
    }

    /**
     * 🎯 특정 모델 지정 호출 (Haiku/Sonnet)
     * WHY: AWS SDK를 사용하여 올바른 SigV4 인증 적용
     */
    public String invokeClaudeSpecific(String modelId, String userMessage) {
        log.info("🤖 Invoking Claude: {} | msg: {}",
                modelId.contains("haiku") ? "⚡ Haiku" : "🧠 Sonnet",
                truncate(userMessage, 100));
        log.info("   Region: {}", bedrockProperties.getRegion());
        log.info("   Model: {}", modelId);

        try {
            // Step 1: Request Body 생성
            String requestBody = buildClaudeRequestBody(userMessage);
            log.debug("📤 Request body length: {} characters", requestBody.length());

            // Step 2: AWS SDK를 통한 API 호출 (올바른 SigV4 인증)
            log.info("🚀 Sending request to Bedrock via AWS SDK...");

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(modelId)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build();

            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);

            // Step 3: 응답 파싱
            String responseBody = response.body().asUtf8String();
            log.info("📥 Response received successfully");
            log.debug("📩 Response body length: {} characters", responseBody.length());

            return parseClaudeResponse(responseBody);

        } catch (software.amazon.awssdk.services.bedrockruntime.model.AccessDeniedException e) {
            log.error("❌ AWS Bedrock 접근 거부!");
            log.error("   1. IAM 사용자에 BedrockFullAccess 권한이 있는지 확인");
            log.error("   2. 리전({})에서 {} 모델이 활성화되어 있는지 확인",
                    bedrockProperties.getRegion(), modelId);
            throw new RuntimeException("AWS Bedrock 접근 거부: IAM 권한 또는 모델 활성화 상태를 확인하세요.", e);

        } catch (software.amazon.awssdk.services.bedrockruntime.model.ValidationException e) {
            log.error("❌ 요청 유효성 검사 실패: {}", e.getMessage());
            throw new RuntimeException("Bedrock 요청 유효성 검사 실패: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("❌ Failed to invoke Claude", e);
            throw new RuntimeException("Claude API 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * Claude Request Body 생성
     */
    private String buildClaudeRequestBody(String userMessage) {
        try {
            // System Prompt (반려동물 건강 전문가)
            String systemPrompt = """
                    당신은 반려동물 건강 전문가입니다.

                    역할:
                    - 반려동물 보호자의 건강 상담에 전문적으로 답변
                    - 증상 분석 및 조치 방법 안내
                    - 병원 방문이 필요한 경우 명확히 권고

                    답변 형식:
                    - 친절하고 이해하기 쉬운 한국어
                    - 구체적이고 실용적인 조언
                    - 의료적 진단이 필요한 경우 반드시 병원 방문 권장

                    제약사항:
                    - 확실하지 않은 진단은 하지 마세요
                    - 약물 처방은 절대 하지 마세요
                    - 응급 상황은 즉시 병원 방문 권고
                    """;

            // Request Body 구성
            var requestBody = objectMapper.createObjectNode();
            requestBody.put("anthropic_version", "bedrock-2023-05-31");
            requestBody.put("max_tokens", bedrockProperties.getMaxTokens());
            requestBody.put("temperature", 0.7);

            // System Prompt 추가
            var systemArray = requestBody.putArray("system");
            var systemObj = systemArray.addObject();
            systemObj.put("type", "text");
            systemObj.put("text", systemPrompt);

            // Messages 추가
            var messagesArray = requestBody.putArray("messages");
            var userMessageObj = messagesArray.addObject();
            userMessageObj.put("role", "user");

            var contentArray = userMessageObj.putArray("content");
            var contentObj = contentArray.addObject();
            contentObj.put("type", "text");
            contentObj.put("text", userMessage);

            String result = objectMapper.writeValueAsString(requestBody);
            log.debug("✅ Request body created successfully");
            return result;

        } catch (Exception e) {
            log.error("❌ Failed to build request body", e);
            throw new RuntimeException("Request body 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Claude 응답 파싱
     */
    private String parseClaudeResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // content 배열에서 첫 번째 text 추출
            JsonNode content = root.path("content");
            if (content.isArray() && content.size() > 0) {
                JsonNode firstContent = content.get(0);
                String text = firstContent.path("text").asText();

                // 토큰 사용량 로깅
                JsonNode usage = root.path("usage");
                int inputTokens = usage.path("input_tokens").asInt();
                int outputTokens = usage.path("output_tokens").asInt();
                log.info("📊 Token usage - Input: {}, Output: {}, Total: {}",
                        inputTokens, outputTokens, inputTokens + outputTokens);

                log.info("✅ Response parsed successfully");
                return text;
            }

            log.warn("⚠️ No content found in response");
            return "응답을 생성할 수 없습니다.";

        } catch (Exception e) {
            log.error("❌ Failed to parse response: {}", responseBody, e);
            throw new RuntimeException("응답 파싱 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 문자열 자르기 (로그용)
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}