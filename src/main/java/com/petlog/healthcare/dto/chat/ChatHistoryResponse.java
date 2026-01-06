package com.petlog.healthcare.dto.chat;

import com.petlog.healthcare.entity.ChatHistory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Chat History 응답 DTO
 * WHY: 채팅 이력을 Frontend에 전달하기 위한 DTO
 */
public class ChatHistoryResponse {

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessage {
        private Long id;
        private String chatType;
        private String userMessage;
        private String botResponse;
        private LocalDateTime createdAt;
        private Boolean userFeedback;

        public static ChatMessage fromEntity(ChatHistory entity) {
            return ChatMessage.builder()
                    .id(entity.getId())
                    .chatType(entity.getChatType())
                    .userMessage(entity.getUserMessage())
                    .botResponse(entity.getBotResponse())
                    .createdAt(entity.getCreatedAt())
                    .userFeedback(entity.getUserFeedback())
                    .build();
        }
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HistoryList {
        private Long petId;
        private int count;
        private List<ChatMessage> messages;

        public static HistoryList of(Long petId, List<ChatHistory> histories) {
            List<ChatMessage> messages = histories.stream()
                    .map(ChatMessage::fromEntity)
                    .collect(Collectors.toList());

            return HistoryList.builder()
                    .petId(petId)
                    .count(messages.size())
                    .messages(messages)
                    .build();
        }
    }
}
