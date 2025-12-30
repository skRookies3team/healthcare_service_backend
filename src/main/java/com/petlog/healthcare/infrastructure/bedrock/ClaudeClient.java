package com.petlog.healthcare.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * AWS Bedrock Claude Client (동기 방식)
 *
 * Claude API 호출을 담당하는 클라이언트
 *
 * WHY 동기 방식?
 * - 스트리밍은 복잡도가 높고 초기 구현에 부담
 * - 일반 상담은 동기 응답으로 충분
 * - 추후 스트리밍 버전을 별도로 구현 가능
 *
 * @author healthcare-team
 * @since 2025-12-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final ObjectMapper objectMapper;

    // ========================================
    // AWS 설정 (환경변수 또는 Properties에서 주입)
    // ========================================
    private static final String AWS_REGION = "us-east-1";  // 또는 환경변수로
    private static final String MODEL_ID = "anthropic.claude-3-5-sonnet-20241022-v2:0";

    /**
     * BedrockRuntimeClient 생성
     *
     * 싱글톤 패턴으로 한 번만 생성
     *
     * @return BedrockRuntimeClient 인스턴스
     */
    private BedrockRuntimeClient createBedrockClient() {
        // AWS Credentials (환경변수에서 로드)
        String accessKey = System.getenv("AWS_ACCESS_KEY_ID");
        String secretKey = System.getenv("AWS_SECRET_ACCESS_KEY");

        if (accessKey == null || secretKey == null) {
            throw new RuntimeException("AWS Credentials not found in environment variables");
        }

        return BedrockRuntimeClient.builder()
                .region(Region.of(AWS_REGION))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey, secretKey)
                        )
                )
                .build();
    }

    /**
     * Claude API 동기 호출
     *
     * 사용자 메시지를 Claude에 전송하고 응답을 반환
     *
     * @param userMessage 사용자 메시지
     * @return Claude의 응답 텍스트
     */
    public String invokeClaude(String userMessage) {
        log.info("Invoking Claude with message: {}", userMessage);

        try (BedrockRuntimeClient client = createBedrockClient()) {

            // ========================================
            // Step 1: Claude Request Body 생성
            // ========================================
            String requestBody = buildClaudeRequestBody(userMessage);
            log.debug("Request body: {}", requestBody);

            // ========================================
            // Step 2: Bedrock API 호출 (동기)
            // ========================================
            InvokeModelRequest invokeRequest = InvokeModelRequest.builder()
                    .modelId(MODEL_ID)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build();

            InvokeModelResponse response = client.invokeModel(invokeRequest);

            // ========================================
            // Step 3: 응답 파싱
            // ========================================
            String responseBody = response.body().asUtf8String();
            log.debug("Response body: {}", responseBody);

            return parseClaudeResponse(responseBody);

        } catch (Exception e) {
            log.error("Failed to invoke Claude", e);
            throw new RuntimeException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Claude Request Body 생성
     *
     * Anthropic Messages API 형식
     * https://docs.anthropic.com/claude/reference/messages_post
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
            requestBody.put("max_tokens", 2000);
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

            return objectMapper.writeValueAsString(requestBody);

        } catch (Exception e) {
            log.error("Failed to build request body", e);
            throw new RuntimeException("Request body 생성 실패", e);
        }
    }

    /**
     * Claude 응답 파싱
     *
     * Claude API 응답 형식:
     * {
     *   "id": "msg_xxx",
     *   "type": "message",
     *   "role": "assistant",
     *   "content": [
     *     {
     *       "type": "text",
     *       "text": "응답 텍스트"
     *     }
     *   ],
     *   "model": "claude-3-5-sonnet-20241022",
     *   "stop_reason": "end_turn",
     *   "usage": {
     *     "input_tokens": 100,
     *     "output_tokens": 200
     *   }
     * }
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

                // 토큰 사용량 로깅 (비용 추적)
                JsonNode usage = root.path("usage");
                int inputTokens = usage.path("input_tokens").asInt();
                int outputTokens = usage.path("output_tokens").asInt();
                log.info("Token usage - Input: {}, Output: {}, Total: {}",
                        inputTokens, outputTokens, inputTokens + outputTokens);

                return text;
            }

            log.warn("No content found in response");
            return "응답을 생성할 수 없습니다.";

        } catch (Exception e) {
            log.error("Failed to parse response", e);
            throw new RuntimeException("응답 파싱 실패", e);
        }
    }
}
