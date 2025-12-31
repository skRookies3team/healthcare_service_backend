package com.petlog.healthcare.service;

import com.petlog.healthcare.infrastructure.bedrock.ClaudeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Claude Service
 *
 * ClaudeClient를 사용한 비즈니스 로직 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeService {

    private final ClaudeClient claudeClient;

    /**
     * 채팅 메시지 처리
     *
     * @param message 사용자 메시지
     * @return Claude 응답
     */
    public String chat(String message) {
        log.info("💬 Processing chat message");

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("메시지가 비어있습니다.");
        }

        try {
            String response = claudeClient.invokeClaude(message);
            log.info("✅ Chat processed successfully");
            return response;
        } catch (Exception e) {
            log.error("❌ Chat processing failed", e);
            throw new RuntimeException("채팅 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
}