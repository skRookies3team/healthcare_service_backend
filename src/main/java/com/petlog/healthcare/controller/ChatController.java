package com.petlog.healthcare.controller;

import com.petlog.healthcare.service.ClaudeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Healthcare AI Chatbot REST API (JSON 파싱 오류 해결)
 *
 * POST /api/chat - Claude 3.5 Haiku 상담
 * POST /test-chat - 테스트용 간단한 엔드포인트
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ClaudeService claudeService;

    /**
     * AI 챗봇 상담 API (String 직접 받기 - 파싱 오류 해결)
     */
    @PostMapping("/api/chat")
    public ResponseEntity<Map<String, String>> chat(@RequestBody String requestBody) {
        log.info("📨 Received chat request: {}", requestBody);

        try {
            // String에서 message 추출
            String message = extractMessage(requestBody);
            log.info("   Message: '{}'", message);

            String response = claudeService.chat(message);
            log.info("✅ Chat request completed successfully");
            return ResponseEntity.ok(Map.of("response", response));
        } catch (Exception e) {
            log.error("❌ Chat request failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 테스트용 간단한 엔드포인트 (String 직접 받기)
     */
    @PostMapping("/test-chat")
    public ResponseEntity<Map<String, String>> testChat(@RequestBody String requestBody) {
        log.info("🧪 TEST - Received chat request: {}", requestBody);

        try {
            // String에서 message 추출
            String message = extractMessage(requestBody);
            if (message == null || message.isBlank()) {
                message = "안녕하세요. 테스트 메시지입니다.";
            }

            log.info("   Test Message: '{}'", message);

            String response = claudeService.chat(message);
            log.info("✅ TEST - Chat completed successfully");
            return ResponseEntity.ok(Map.of(
                    "success", "true",
                    "message", message,
                    "response", response
            ));
        } catch (Exception e) {
            log.error("❌ TEST - Chat failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", "false",
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * JSON String에서 message 추출 (파싱 오류 방지)
     */
    private String extractMessage(String requestBody) {
        if (requestBody == null) return "기본 메시지";

        // {"message": "안녕하세요"} 형식에서 message 추출
        if (requestBody.contains("\"message\"")) {
            String[] parts = requestBody.split("\"message\"\\s*:\\s*\"");
            if (parts.length > 1) {
                String messagePart = parts[1].split("\"")[0];
                return messagePart.replace("\\u", ""); // 유니코드 이스케이프 제거
            }
        }

        // 단순 텍스트인 경우
        return requestBody.trim().replaceAll("[{}\"]", "");
    }
}
