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

/**
 * AWS Bedrock Claude Client (Bearer Token 방식 + Dual Models)
 * ✅ invokeClaude() 메서드 포함 (기본값)
 * ✅ invokeClaudeSpecific() 메서드 (모델 지정)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final ObjectMapper objectMapper;
    private final BedrockProperties bedrockProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /**
     * ✅ 기존 메서드 유지: Sonnet 기본 호출
     * ClaudeService에서 사용
     */
    public String invokeClaude(String userMessage) {
        log.info("🤖 [기본 Sonnet] invokeClaude() 호출: {}", truncate(userMessage, 100));
        return invokeClaudeSpecific(bedrockProperties.getModelId(), userMessage);
    }

    /**
     * 🎯 특정 모델 지정 호출 (Haiku/Sonnet)
     * Haiku 또는 다른 모델 사용 시 이 메서드 사용
     */
    public String invokeClaudeSpecific(String modelId, String userMessage) {
        log.info("🤖 Invoking Claude: {} | msg: {}",
                modelId.contains("haiku") ? "⚡ Haiku" : "🧠 Sonnet",
                truncate(userMessage, 100));
        log.info("   Region: {}", bedrockProperties.getRegion());

        try {
            // Step 1: API 엔드포인트 구성 (ap-northeast-2 한국 리전)
            String endpoint = String.format(
                    "https://bedrock-runtime.%s.amazonaws.com/model/%s/invoke",
                    bedrockProperties.getRegion(),
                    modelId
            );
            log.debug("📍 Endpoint: {}", endpoint);

            // Step 2: Request Body 생성
            String requestBody = buildClaudeRequestBody(userMessage);
            log.debug("📤 Request body length: {} characters", requestBody.length());

            // Step 3: HTTP 요청 생성 (Bearer Token 인증)
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + bedrockProperties.getApiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .timeout(Duration.ofSeconds(60))
                    .build();

            log.info("🚀 Sending request to Bedrock (ap-northeast-2)...");

            // Step 4: HTTP 요청 실행
            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );

            // Step 5: 응답 상태 확인
            log.info("📥 Response status: {}", response.statusCode());

            if (response.statusCode() != 200) {
                String errorBody = response.body();
                log.error("❌ Bedrock API 호출 실패");
                log.error("   Status: {}", response.statusCode());
                log.error("   Region: {}", bedrockProperties.getRegion());
                log.error("   Model: {}", modelId);
                log.error("   Error Body: {}", errorBody);

                // 상세한 에러 메시지 제공
                if (response.statusCode() == 401) {
                    throw new RuntimeException("인증 실패: API 키를 확인해주세요. (401 Unauthorized)");
                } else if (response.statusCode() == 403) {
                    throw new RuntimeException("접근 거부: API 키 권한 또는 리전(ap-northeast-2) 설정을 확인해주세요. (403 Forbidden)");
                } else if (response.statusCode() == 404) {
                    throw new RuntimeException("모델을 찾을 수 없습니다: 모델 ID 또는 리전을 확인해주세요. (404 Not Found)");
                } else {
                    throw new RuntimeException("Bedrock API 호출 실패: " + response.statusCode() + " - " + errorBody);
                }
            }

            // Step 6: 응답 파싱
            String responseBody = response.body();
            log.debug("📩 Response body length: {} characters", responseBody.length());

            return parseClaudeResponse(responseBody);

        } catch (RuntimeException e) {
            throw e; // 이미 처리된 예외는 그대로 전달
        } catch (Exception e) {
            log.error("❌ Failed to invoke Claude", e);
            throw new RuntimeException("Claude API 호출 중 오류 발생: " + e.getMessage(), e);
        }
    }

    /**
     * Claude Request Body 생성 (당신의 기존 코드 완전 복사)
     *
     * Anthropic Messages API 형식 (Bedrock용)
     *
     * @param userMessage 사용자 메시지
     * @return JSON 문자열
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
     * Claude 응답 파싱 (당신의 기존 코드 완전 복사)
     *
     * @param responseBody Claude API 응답 JSON
     * @return 응답 텍스트
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