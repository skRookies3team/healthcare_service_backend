package com.petlog.healthcare.controller;

import com.petlog.healthcare.service.ClaudeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE 스트리밍 채팅 컨트롤러
 * WHY: 실시간 AI 응답 스트리밍으로 UX 개선
 *
 * SSE (Server-Sent Events) 방식으로 AI 응답을 청크 단위로 전송
 *
 * @author healthcare-team
 * @since 2026-01-06
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class StreamingChatController {

    private final ClaudeService claudeService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    /**
     * SSE 스트리밍 채팅
     *
     * @param message 사용자 메시지
     * @return SseEmitter (text/event-stream)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestParam String message) {
        log.info("🌊 SSE 스트리밍 시작 - message: {}", message);

        // 60초 타임아웃
        SseEmitter emitter = new SseEmitter(60000L);

        executor.execute(() -> {
            try {
                // AI 응답 생성
                String fullResponse = claudeService.chatHaiku(message);

                // 청크 단위로 분할하여 전송 (50자씩)
                int chunkSize = 50;
                for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, fullResponse.length());
                    String chunk = fullResponse.substring(i, end);

                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(Map.of(
                                    "chunk", chunk,
                                    "done", false)));

                    // 스트리밍 효과를 위한 지연
                    Thread.sleep(50);
                }

                // 완료 이벤트
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(Map.of(
                                "done", true,
                                "fullResponse", fullResponse)));

                emitter.complete();
                log.info("✅ SSE 스트리밍 완료");

            } catch (IOException | InterruptedException e) {
                log.error("❌ SSE 스트리밍 오류", e);
                emitter.completeWithError(e);
            }
        });

        emitter.onCompletion(() -> log.debug("SSE 연결 종료"));
        emitter.onTimeout(() -> log.warn("SSE 타임아웃"));
        emitter.onError(e -> log.error("SSE 에러", e));

        return emitter;
    }

    /**
     * Persona 채팅 스트리밍 (petId 필요)
     */
    @GetMapping(value = "/stream/persona/{petId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPersonaChat(
            @PathVariable Long petId,
            @RequestParam String message,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "1") Long userId) {

        log.info("🌊 Persona SSE 스트리밍 - petId: {}, userId: {}", petId, userId);

        SseEmitter emitter = new SseEmitter(60000L);

        executor.execute(() -> {
            try {
                // AI 응답 생성 (Haiku로 빠르게)
                String fullResponse = claudeService.chatHaiku(
                        String.format("[펫 ID: %d] %s", petId, message));

                // 청크 단위로 분할하여 전송
                int chunkSize = 30;
                for (int i = 0; i < fullResponse.length(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, fullResponse.length());
                    String chunk = fullResponse.substring(i, end);

                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(Map.of("chunk", chunk, "done", false)));

                    Thread.sleep(30);
                }

                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(Map.of("done", true, "petId", petId)));

                emitter.complete();

            } catch (IOException | InterruptedException e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
}
