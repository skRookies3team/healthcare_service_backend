package com.petlog.healthcare.api.controller;

import com.petlog.healthcare.api.dto.request.PersonaChatRequest;
import com.petlog.healthcare.api.dto.response.PersonaChatResponse;
import com.petlog.healthcare.domain.service.PersonaChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Persona Chat API Controller
 * RAG 기반 개인화 챗봇 엔드포인트
 *
 * WHY? REST API를 통해 Frontend에서 쉽게 접근 가능
 * 요청/응답 검증(Validation)은 컨트롤러에서 담당
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class PersonaChatController {

    private final PersonaChatService personaChatService;

    /**
     * Persona Chat 엔드포인트
     * POST /api/chat/persona
     *
     * Request Body:
     * {
     *   "userId": 1,
     *   "petId": 5,
     *   "message": "우리 강아지가 최근에 잘 지내고 있나요?"
     * }
     *
     * Response:
     * {
     *   "answer": "최근 일기를 보니...",
     *   "relatedDiaries": [1, 2, 3],
     *   "timestamp": "2025-01-02T13:00:00"
     * }
     *
     * @param request 페르소나 챗 요청
     * @return 페르소나 챗 응답 (RAG 포함)
     */
    @PostMapping("/persona")
    public ResponseEntity<PersonaChatResponse> personaChat(
            @Valid @RequestBody PersonaChatRequest request
    ) {
        log.info("POST /api/chat/persona - userId: {}, petId: {}",
                request.getUserId(), request.getPetId());

        try {
            PersonaChatResponse response = personaChatService.chat(
                    request.getUserId(),
                    request.getPetId(),
                    request.getMessage()
            );

            log.info("Persona chat response generated successfully");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error processing persona chat request", e);
            throw new RuntimeException("챗봇 요청 처리 중 오류 발생", e);
        }
    }
}
