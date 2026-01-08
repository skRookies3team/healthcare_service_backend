package com.petlog.healthcare.controller;

import com.petlog.healthcare.service.ClaudeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * ChatController - 당신의 기존 코드 + Dual Models
 * 당신의 기존 로직 100% 유지
 *
 * /api/chat/health - 헬스체크
 * /api/chat/test-chat - 기존 테스트 (Sonnet + RAG)
 * /api/chat/haiku - 신규 빠른 채팅 (Haiku)
 * /api/chat/persona - 신규 페르소나 (Sonnet + 강화 RAG)
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

    /**
     * 기존 health 엔드포인트 - 완전 동일
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Healthcare AI Chatbot",
                "models", "Sonnet (default), Haiku (fast)",
                "port", "8085"
        ));
    }

    /**
     * 기존 test-chat 엔드포인트 - 완전 동일 (Sonnet + RAG)
     */
    @PostMapping("/test-chat")
    public ResponseEntity<Map<String, Object>> testChat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = claudeService.chat(message);  // 기존 메서드 (Sonnet + RAG)
        return ResponseEntity.ok(Map.of(
                "success", true,
                "model", "Sonnet",
                "response", response
        ));
    }

    /**
     * 신규: Haiku 빠른 채팅 엔드포인트
     *
     * POST /api/chat/haiku
     * {
     *   "message": "강아지 건강 팁"
     * }
     */
    @PostMapping("/haiku")
    public ResponseEntity<Map<String, Object>> chatHaiku(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message required"));
        }

        try {
            String response = claudeService.chatHaiku(message);  // 신규 메서드 (Haiku)
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "model", "Claude Haiku (Fast)",
                    "response", response
            ));
        } catch (Exception e) {
            log.error("❌ Haiku chat failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * 신규: Sonnet 페르소나 채팅 엔드포인트 (강화 RAG)
     *
     * POST /api/chat/persona
     * {
     *   "message": "최근 증상이 있어"
     * }
     */
    @PostMapping("/persona")
    public ResponseEntity<Map<String, Object>> chatPersona(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message required"));
        }

        try {
            String response = claudeService.chatPersona(message);  // 신규 메서드 (Sonnet + 강화 RAG)
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "model", "Claude Sonnet (Persona+RAG)",
                    "response", response
            ));
        } catch (Exception e) {
            log.error("❌ Persona chat failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}