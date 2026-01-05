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
                "port", "8085"));
    }

    /**
     * 기존 test-chat 엔드포인트 - 완전 동일 (Sonnet + RAG)
     */
    @PostMapping("/test-chat")
    public ResponseEntity<Map<String, Object>> testChat(@RequestBody Map<String, String> request) {
        String message = request.get("message");
        String response = claudeService.chat(message); // 기존 메서드 (Sonnet + RAG)
        return ResponseEntity.ok(Map.of(
                "success", true,
                "model", "Sonnet",
                "response", response));
    }

    /**
     * 신규: Haiku 빠른 채팅 엔드포인트
     *
     * POST /api/chat/haiku
     * {
     * "message": "강아지 건강 팁"
     * }
     */
    @PostMapping("/haiku")
    public ResponseEntity<Map<String, Object>> chatHaiku(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message required"));
        }

        try {
            String response = claudeService.chatHaiku(message); // 신규 메서드 (Haiku)
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "model", "Claude Haiku (Fast)",
                    "response", response));
        } catch (Exception e) {
            log.error("❌ Haiku chat failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }

    /**
     * 신규: Sonnet 페르소나 채팅 엔드포인트 (강화 RAG)
     *
     * POST /api/chat/persona
     * {
     * "message": "요즘 자꾸 배가 아파",
     * "petId": "1",
     * "petProfile": "이름: 초코, 종: 골든리트리버, 나이: 3살",
     * "healthHistory": "최근 구토 2회, 식욕 감소",
     * "recentDiary": "오늘 산책 중 기운이 없었어요",
     * "emotion": "걱정됨",
     * "date": "2026-01-05"
     * }
     */
    @PostMapping("/persona")
    public ResponseEntity<Map<String, Object>> chatPersona(@RequestBody Map<String, String> request) {
        String message = request.get("message");

        if (message == null || message.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "message required"));
        }

        try {
            // ClaudeService.chatPersona()가 요구하는 7개 인자 추출
            String petId = request.getOrDefault("petId", "unknown");
            String petProfile = request.getOrDefault("petProfile", "정보 없음");
            String healthHistory = request.getOrDefault("healthHistory", "건강 기록 없음");
            String recentDiary = request.getOrDefault("recentDiary", "최근 일기 없음");
            String emotion = request.getOrDefault("emotion", "보통");
            String date = request.getOrDefault("date", java.time.LocalDate.now().toString());

            // ClaudeService.chatPersona(message, petId, petProfile, healthHistory,
            // recentDiary, emotion, date)
            String response = claudeService.chatPersona(
                    message,
                    petId,
                    petProfile,
                    healthHistory,
                    recentDiary,
                    emotion,
                    date);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "model", "Claude Sonnet (Persona+RAG)",
                    "petId", petId,
                    "response", response));
        } catch (Exception e) {
            log.error("❌ Persona chat failed", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()));
        }
    }
}