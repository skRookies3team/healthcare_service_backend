package com.petlog.healthcare.infrastructure.bedrock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petlog.healthcare.config.BedrockProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelWithResponseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ResponseStream;

import java.nio.charset.StandardCharsets;

/**
 * AWS Bedrock Claude API Client (동기 + 비동기 스트리밍)
 *
 * Claude 3.5 Haiku 모델을 호출하여 AI 응답을 생성하는 클라이언트
 *
 * 주요 기능:
 * - 동기 호출 (invokeClaude): 전체 응답 대기
 * - 비동기 스트리밍 (invokeClaudeStreaming): 실시간 응답 스트리밍
 *
 * WHY 스트리밍?
 * - 사용자 경험 향상 (ChatGPT처럼 실시간 응답)
 * - 긴 응답도 빠르게 시작
 * - 논블로킹 방식으로 서버 성능 향상
 *
 * @author healthcare-team
 * @since 2025-12-19
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClaudeClient {

    private final BedrockRuntimeClient bedrockRuntimeClient;
    private final BedrockProperties bedrockProperties;
    private final ObjectMapper objectMapper;

    /**
     * Claude API 호출 (동기 방식)
     *
     * 전체 응답이 완성될 때까지 대기
     * 간단한 요청/응답에 사용
     *
     * @param userMessage 사용자 입력 메시지
     * @return Claude의 완성된 응답 텍스트
     */
    public String invokeClaude(String userMessage) {
        log.info("Invoking Claude (sync) with message: {}", userMessage);

        try {
            // 1. 요청 JSON 생성
            String requestBody = buildRequestBody(userMessage);
            log.debug("Request body: {}", requestBody);

            // 2. Bedrock API 호출
            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(bedrockProperties.getModelId())
                    .body(SdkBytes.fromUtf8String(requestBody))
                    .build();

            InvokeModelResponse response = bedrockRuntimeClient.invokeModel(request);

            // 3. 응답 JSON 파싱
            String responseBody = response.body().asUtf8String();
            log.debug("Response body: {}", responseBody);

            String claudeResponse = parseResponse(responseBody);
            log.info("Claude response: {}", claudeResponse);

            return claudeResponse;

        } catch (Exception e) {
            log.error("Failed to invoke Claude (sync)", e);
            throw new RuntimeException("Claude API 호출 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Claude API 호출 (비동기 스트리밍 방식)
     *
     * 응답을 실시간으로 스트리밍
     * ChatGPT처럼 글자가 하나씩 나타나는 효과
     *
     * @param userMessage 사용자 입력 메시지
     * @return Flux<String> - 실시간 응답 스트림
     *
     * WHY Flux?
     * - Reactor의 비동기 스트림 타입
     * - 여러 개의 데이터를 순차적으로 발행
     * - Server-Sent Events (SSE)와 호환
     *
     * 사용 예시 (Controller):
     * @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
     * public Flux<String> chatStream(@RequestParam String message) {
     *     return claudeClient.invokeClaudeStreaming(message);
     * }
     */
    public Flux<String> invokeClaudeStreaming(String userMessage) {
        log.info("Invoking Claude (streaming) with message: {}", userMessage);

        return Flux.create(sink -> {
            try {
                // 1. 요청 JSON 생성
                String requestBody = buildRequestBody(userMessage);
                log.debug("Request body: {}", requestBody);

                // 2. Bedrock Streaming API 호출
                InvokeModelWithResponseStreamRequest request =
                        InvokeModelWithResponseStreamRequest.builder()
                                .modelId(bedrockProperties.getModelId())
                                .body(SdkBytes.fromUtf8String(requestBody))
                                .build();

                // 3. 스트리밍 응답 처리
                bedrockRuntimeClient.invokeModelWithResponseStream(request, responseHandler -> {
                    responseHandler.onEventStream(stream -> {
                        stream.forEach(event -> {
                            event.accept(new ResponseStream.Visitor() {
                                @Override
                                public void visitChunk(ResponseStream.Chunk chunk) {
                                    // 각 청크를 파싱하여 텍스트 추출
                                    String chunkText = parseStreamChunk(chunk.bytes().asUtf8String());
                                    if (!chunkText.isEmpty()) {
                                        sink.next(chunkText);  // 스트림에 데이터 발행
                                    }
                                }

                                @Override
                                public void visitDefault(ResponseStream event) {
                                    // 기타 이벤트 처리
                                }
                            });
                        });
                    });

                    responseHandler.onComplete(() -> {
                        log.info("Streaming completed");
                        sink.complete();  // 스트림 종료
                    });

                    responseHandler.exceptionOccurred(throwable -> {
                        log.error("Streaming error", throwable);
                        sink.error(new RuntimeException("스트리밍 실패", throwable));
                    });
                });

            } catch (Exception e) {
                log.error("Failed to invoke Claude (streaming)", e);
                sink.error(new RuntimeException("Claude API 스트리밍 실패: " + e.getMessage(), e));
            }
        });
    }

    /**
     * Claude API 요청 Body 생성
     *
     * Claude Messages API 형식:
     * {
     *   "anthropic_version": "bedrock-2023-05-31",
     *   "max_tokens": 1000,
     *   "messages": [
     *     {
     *       "role": "user",
     *       "content": "사용자 메시지"
     *     }
     *   ]
     * }
     *
     * @param userMessage 사용자 입력
     * @return JSON 형식의 요청 Body
     */
    private String buildRequestBody(String userMessage) {
        try {
            return objectMapper.writeValueAsString(
                    new RequestBody(
                            "bedrock-2023-05-31",
                            bedrockProperties.getMaxTokens(),
                            new Message[]{
                                    new Message("user", userMessage)
                            }
                    )
            );
        } catch (Exception e) {
            log.error("Failed to build request body", e);
            throw new RuntimeException("요청 JSON 생성 실패", e);
        }
    }

    /**
     * Claude API 응답 파싱 (동기 방식)
     *
     * @param responseBody JSON 응답
     * @return Claude의 응답 텍스트
     */
    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("content").get(0);
            return content.path("text").asText();

        } catch (Exception e) {
            log.error("Failed to parse response", e);
            throw new RuntimeException("응답 JSON 파싱 실패", e);
        }
    }

    /**
     * 스트리밍 청크 파싱
     *
     * 각 청크에서 delta 텍스트 추출
     *
     * 청크 형식:
     * {
     *   "type": "content_block_delta",
     *   "delta": {
     *     "type": "text_delta",
     *     "text": "안"
     *   }
     * }
     *
     * @param chunkBody JSON 청크
     * @return 추출된 텍스트 (없으면 빈 문자열)
     */
    private String parseStreamChunk(String chunkBody) {
        try {
            JsonNode root = objectMapper.readTree(chunkBody);

            // content_block_delta 이벤트에서 텍스트 추출
            if ("content_block_delta".equals(root.path("type").asText())) {
                return root.path("delta").path("text").asText("");
            }

            return "";

        } catch (Exception e) {
            log.warn("Failed to parse stream chunk: {}", chunkBody, e);
            return "";
        }
    }

    /**
     * 요청 Body DTO (내부 클래스)
     */
    private record RequestBody(
            String anthropic_version,
            Integer max_tokens,
            Message[] messages
    ) {}

    private record Message(
            String role,
            String content
    ) {}
}
