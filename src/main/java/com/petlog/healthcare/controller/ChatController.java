package com.petlog.healthcare.controller;

import com.petlog.healthcare.service.ChatHistoryService;
import com.petlog.healthcare.service.ClaudeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ChatController - Dual Models + Chat History 저장
 *
 * /api/chat/health - 헬스체크
 * /api/chat/test-chat - 기존 테스트 (Sonnet + RAG)
 * /api/chat/haiku - 빠른 채팅 (Haiku)
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ClaudeService claudeService;
    private final ChatHistoryService chatHistoryService;

    /**
     * 기존 health 엔드포인트
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Healthcare AI Chatbot",
                "models", "Sonnet (default), Haiku (fast)",
                "features", "SSE Streaming, Chat History",
                "port", "8085"));
    }

    /**
     * 기존 test-chat 엔드포인트 (Sonnet + RAG) + 히스토리 저장
     */
    @PostMapping("/test-chat")
    public ResponseEntity<Map<String, Object>> testChat(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-PET-ID", required = false, defaultValue = "0") Long petId) {

        String message = request.get("message");
        long startTime = System.currentTimeMillis();

        String response = claudeService.chat(message);

        int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

        // 히스토리 저장 (userId가 있을 때만)
        if (userId > 0 && petId > 0) {
            chatHistoryService.saveChat(userId, petId, "GENERAL", message, response, responseTimeMs);
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "model", "Sonnet",
                "responseTimeMs", responseTimeMs,
                "response", response));
    }

    /**
     * Haiku 빠른 채팅 + 히스토리 저장
     */
    @PostMapping("/haiku")
    public ResponseEntity<Map<String, Object>> chatHaiku(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-PET-ID", required = false, defaultValue = "0") Long petId) {

        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message required"));
        }

        try {
            long startTime = System.currentTimeMillis();
            String response = claudeService.chatHaiku(message);
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

            // 히스토리 저장 (userId가 있을 때만)
            if (userId > 0 && petId > 0) {
                chatHistoryService.saveChat(userId, petId, "QUICK", message, response, responseTimeMs);
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "model", "Claude Haiku (Fast)",
                    "responseTimeMs", responseTimeMs,
                    "response", response));
        } catch (Exception e) {
            log.error("❌ Haiku chat failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }
}