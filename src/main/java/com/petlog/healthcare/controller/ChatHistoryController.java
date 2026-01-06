package com.petlog.healthcare.controller;

import com.petlog.healthcare.dto.chat.ChatHistoryResponse;
import com.petlog.healthcare.service.ChatHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Chat History 조회 컨트롤러
 * WHY: 채팅 이력 조회 및 피드백 관리 API
 *
 * @author healthcare-team
 * @since 2026-01-06
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/history")
@RequiredArgsConstructor
@Tag(name = "Chat History", description = "채팅 이력 조회 API")
public class ChatHistoryController {

    private final ChatHistoryService chatHistoryService;

    /**
     * 최근 채팅 이력 조회
     *
     * @param petId  펫 ID
     * @param userId 사용자 ID (Gateway에서 주입)
     * @param limit  조회 개수 (기본 20)
     */
    @GetMapping("/{petId}")
    @Operation(summary = "채팅 이력 조회", description = "특정 펫의 최근 채팅 이력을 조회합니다")
    public ResponseEntity<ChatHistoryResponse.HistoryList> getHistory(
            @PathVariable Long petId,
            @RequestHeader("X-USER-ID") Long userId,
            @RequestParam(defaultValue = "20") int limit) {

        log.info("📜 채팅 이력 조회 요청 - petId: {}, userId: {}", petId, userId);

        ChatHistoryResponse.HistoryList history = chatHistoryService.getRecentHistory(userId, petId, limit);

        return ResponseEntity.ok(history);
    }

    /**
     * 채팅 타입별 이력 조회
     */
    @GetMapping("/{petId}/type/{chatType}")
    @Operation(summary = "타입별 채팅 이력 조회", description = "GENERAL, PERSONA, QUICK 타입별 이력 조회")
    public ResponseEntity<ChatHistoryResponse.HistoryList> getHistoryByType(
            @PathVariable Long petId,
            @PathVariable String chatType,
            @RequestHeader("X-USER-ID") Long userId) {

        log.info("📜 타입별 채팅 이력 조회 - petId: {}, type: {}", petId, chatType);

        ChatHistoryResponse.HistoryList history = chatHistoryService.getHistoryByType(userId, petId, chatType);

        return ResponseEntity.ok(history);
    }

    /**
     * 피드백 제출
     */
    @PostMapping("/{historyId}/feedback")
    @Operation(summary = "피드백 제출", description = "채팅 응답에 대한 피드백 저장")
    public ResponseEntity<Map<String, Object>> submitFeedback(
            @PathVariable Long historyId,
            @RequestBody Map<String, Boolean> request) {

        Boolean feedback = request.get("liked");
        log.info("👍 피드백 제출 - historyId: {}, liked: {}", historyId, feedback);

        chatHistoryService.updateFeedback(historyId, feedback);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "피드백이 저장되었습니다"));
    }
}
