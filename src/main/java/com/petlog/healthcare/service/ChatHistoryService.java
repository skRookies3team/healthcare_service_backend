package com.petlog.healthcare.service;

import com.petlog.healthcare.domain.repository.ChatHistoryRepository;
import com.petlog.healthcare.dto.chat.ChatHistoryResponse;
import com.petlog.healthcare.entity.ChatHistory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chat History 서비스
 * WHY: 채팅 이력 저장 및 조회 통합 관리
 *
 * @author healthcare-team
 * @since 2026-01-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private final ChatHistoryRepository chatHistoryRepository;

    /**
     * 채팅 이력 저장
     */
    @Transactional
    public void saveChat(Long userId, Long petId, String chatType,
            String userMessage, String botResponse, Integer responseTimeMs) {
        try {
            ChatHistory history = ChatHistory.builder()
                    .userId(userId)
                    .petId(petId)
                    .chatType(chatType)
                    .userMessage(userMessage)
                    .botResponse(botResponse)
                    .responseTimeMs(responseTimeMs)
                    .createdAt(LocalDateTime.now())
                    .build();

            chatHistoryRepository.save(history);
            log.debug("✅ 채팅 이력 저장 완료 - userId: {}, petId: {}, type: {}", userId, petId, chatType);
        } catch (Exception e) {
            log.error("❌ 채팅 이력 저장 실패", e);
            // Graceful degradation - 저장 실패해도 서비스는 계속
        }
    }

    /**
     * 최근 채팅 이력 조회
     */
    @Transactional(readOnly = true)
    public ChatHistoryResponse.HistoryList getRecentHistory(Long userId, Long petId, int limit) {
        log.info("📜 채팅 이력 조회 - userId: {}, petId: {}, limit: {}", userId, petId, limit);

        List<ChatHistory> histories = chatHistoryRepository.findRecentChats(userId, petId, limit);

        return ChatHistoryResponse.HistoryList.of(petId, histories);
    }

    /**
     * 특정 채팅 타입 이력 조회
     */
    @Transactional(readOnly = true)
    public ChatHistoryResponse.HistoryList getHistoryByType(Long userId, Long petId, String chatType) {
        log.info("📜 타입별 채팅 이력 조회 - userId: {}, petId: {}, type: {}", userId, petId, chatType);

        List<ChatHistory> histories = chatHistoryRepository.findByUserIdAndPetIdAndChatType(userId, petId, chatType);

        return ChatHistoryResponse.HistoryList.of(petId, histories);
    }

    /**
     * 피드백 업데이트
     */
    @Transactional
    public void updateFeedback(Long historyId, Boolean feedback) {
        chatHistoryRepository.findById(historyId).ifPresent(history -> {
            history.setFeedback(feedback);
            chatHistoryRepository.save(history);
            log.info("👍 피드백 저장 - historyId: {}, feedback: {}", historyId, feedback);
        });
    }
}
