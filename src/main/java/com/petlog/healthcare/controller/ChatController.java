package com.petlog.healthcare.controller;

import com.petlog.healthcare.service.ChatHistoryService;
import com.petlog.healthcare.service.ClaudeService;
import com.petlog.healthcare.service.SmartChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ChatController - Dual Models + Chat History 저장 + Smart Chat
 *
 * /api/chat/health - 헬스체크
 * /api/chat/test-chat - 기존 테스트 (Sonnet + RAG)
 * /api/chat/haiku - 빠른 채팅 (Haiku)
 * /api/chat/smart - 스마트 챗봇 (피부질환/병원 연동)
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
    private final SmartChatService smartChatService;

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

    /**
     * 스마트 챗봇 - 피부질환/동물병원 자동 연동
     *
     * 사용자 질문을 분석하여:
     * - 피부 관련 → 피부질환 탐지 기능 안내
     * - 병원 관련 → 동물병원 검색 결과 포함
     * - 일반 질문 → 수의사 모드 응답
     */
    @PostMapping("/smart")
    public ResponseEntity<Map<String, Object>> smartChat(
            @RequestBody Map<String, String> request,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-PET-ID", required = false, defaultValue = "0") Long petId) {

        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message required"));
        }

        try {
            log.info("═══════════════════════════════════════");
            log.info("🧠 스마트 챗봇 요청");
            log.info("   User-Id: {}, Pet-Id: {}", userId, petId);
            log.info("   Message: {}", message.length() > 50 ? message.substring(0, 50) + "..." : message);
            log.info("═══════════════════════════════════════");

            long startTime = System.currentTimeMillis();
            Map<String, Object> response = smartChatService.smartChat(message, userId, petId);
            int responseTimeMs = (int) (System.currentTimeMillis() - startTime);

            // 히스토리 저장
            if (userId > 0 && petId > 0) {
                String intent = (String) response.getOrDefault("intent", "GENERAL");
                chatHistoryService.saveChat(userId, petId, "SMART_" + intent,
                        message, (String) response.get("response"), responseTimeMs);
            }

            response.put("responseTimeMs", responseTimeMs);
            log.info("✅ 스마트 챗봇 완료 - 의도: {}, 응답시간: {}ms",
                    response.get("intent"), responseTimeMs);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ 스마트 챗봇 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }
}