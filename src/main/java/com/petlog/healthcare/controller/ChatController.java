package com.petlog.healthcare.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.petlog.healthcare.service.ClaudeService;

import java.util.HashMap;
import java.util.Map;

/**
 * Healthcare AI Chatbot API (Bedrock 직접 테스트)
 *
 * WHY Gateway 없이 테스트?
 * - 8085 포트 직접 접근으로 네트워크 변수 최소화
 * - Bedrock API Key 검증 우선
 * - Claude 응답 검증 (Gateway 오류와 분리)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ChatController {

    private final ClaudeService claudeService;

    /**
     * 헬스체크 (Bedrock 연결 확인)
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "Healthcare AI Chatbot");
        status.put("port", "8085");
        status.put("bedrock", "ready");
        log.info("✅ Health check OK");
        return ResponseEntity.ok(status);
    }

    /**
     * Bedrock Claude 테스트 (독립적 호출)
     *
     * POST /test-chat
     * Body: {"message": "강아지가 밥을 안 먹어요"}
     * Response: {"response": "Claude 답변..."}
     */
    @PostMapping("/test-chat")
    public ResponseEntity<Map<String, Object>> testChat(@RequestBody Map<String, String> request) {
        String userMessage = request.get("message");
        log.info("🤖 사용자 메시지: {}", userMessage);

        // Bedrock Claude 호출 (Gateway 없음)
        String claudeResponse = claudeService.chat(userMessage);

        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("response", claudeResponse);
        response.put("model", "claude-3.5-haiku");

        log.info("✅ Claude 응답 수신 완료");
        return ResponseEntity.ok(response);
    }
}
