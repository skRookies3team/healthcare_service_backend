package com.petlog.healthcare.controller;

import com.petlog.healthcare.infrastructure.bedrock.ClaudeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 챗봇 API Controller
 *
 * 동기 방식 Claude API 호출
 *
 * @author healthcare-team
 * @since 2025-12-23
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ClaudeClient claudeClient;

    /**
     * 동기 방식 챗봇 API
     *
     * POST /api/chat
     * {
     *   "message": "강아지가 구토해요"
     * }
     *
     * 응답:
     * {
     *   "response": "구토의 원인은..."
     * }
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Chat request: {}", request.message());

        try {
            // Claude API 호출
            String response = claudeClient.invokeClaude(request.message());

            return ResponseEntity.ok(new ChatResponse(response));

        } catch (Exception e) {
            log.error("Chat failed", e);

            return ResponseEntity
                    .internalServerError()
                    .body(new ChatResponse("죄송합니다. 일시적인 오류가 발생했습니다."));
        }
    }

    /**
     * Health Check
     *
     * GET /api/chat/health
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Healthcare Chatbot Service is running!");
    }

    // ========================================
    // DTOs
    // ========================================

    public record ChatRequest(String message) {}

    public record ChatResponse(String response) {}
}
