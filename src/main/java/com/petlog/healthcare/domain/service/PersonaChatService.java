package com.petlog.healthcare.domain.service;

import com.petlog.healthcare.api.dto.response.PersonaChatResponse;
import com.petlog.healthcare.config.BedrockConfig.BedrockProperties;
import com.petlog.healthcare.domain.entity.ChatHistory;
import com.petlog.healthcare.domain.entity.DiaryMemory;
import com.petlog.healthcare.domain.repository.ChatHistoryRepository;
import com.petlog.healthcare.domain.repository.DiaryMemoryRepository;
import com.petlog.healthcare.infrastructure.bedrock.ClaudeClient;
import com.petlog.healthcare.infrastructure.milvus.MilvusVectorStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persona Chat Service (DDD 패턴)
 * RAG (Retrieval-Augmented Generation)를 활용한 개인화된 챗봇 서비스
 *
 * WHY? 사용자의 일기 기록과 건강 데이터를 바탕으로 Claude Sonnet이
 * 더 정확하고 개인화된 응답 생성
 *
 * Architecture:
 * 1. 사용자 메시지 수신
 * 2. Milvus에서 관련 일기 벡터 검색 (Top 3)
 * 3. 관련 일기 + 건강 기록으로 Context 구성
 * 4. Claude Sonnet에 요청 (Context + System Prompt)
 * 5. Chat History에 저장 및 응답 반환
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PersonaChatService {

    // ✅ 의존성 주입 (DI) 완성
    private final ClaudeClient claudeClient;
    private final MilvusVectorStore milvusVectorStore;
    private final DiaryMemoryRepository diaryMemoryRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final HealthRecordService healthRecordService;
    private final BedrockProperties bedrockProperties;  // ✅ 추가됨

    // System Prompt for Persona Chat
    private static final String PERSONA_SYSTEM_PROMPT = """
        당신은 반려동물의 건강과 행복을 전담하는 AI 건강 도우미입니다.
        
        역할:
        - 반려동물의 과거 일기, 건강 기록을 기반으로 개인화된 조언 제공
        - 특정 일기나 건강 패턴에 대해 깊이 있는 피드백
        - 따뜻하고 공감하는 톤으로 의사소통
        - 반려동물 건강에 대한 신뢰할 수 있는 정보 제공
        
        가이드라인:
        - 사용자가 제시한 구체적인 일기나 건강 기록을 참고하여 답변
        - 반려동물의 건강 추이나 패턴을 분석하여 조언
        - 심각한 건강 문제는 수의사 상담 권장
        - 항상 한국어로 응답
        - 응답은 300-500자 범위 내로 유지
        """;

    /**
     * Persona Chat 실행 (RAG 기반)
     *
     * Flow:
     * 1. 사용자 메시지 → 벡터화
     * 2. Milvus 유사도 검색 → 관련 일기 Top 3
     * 3. 일기 + 건강기록 Context 생성
     * 4. Claude Sonnet 호출 (invokeClaudeSpecific 사용)
     * 5. Chat History 저장
     *
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param userMessage 사용자 메시지
     * @return PersonaChatResponse (답변 + 관련 일기 ID)
     */
    @Transactional
    public PersonaChatResponse chat(Long userId, Long petId, String userMessage) {
        log.info("🧠 [Persona Chat] userId: {}, petId: {}, message: {}",
                userId, petId, truncate(userMessage, 50));

        try {
            // Step 1: 관련 일기 검색 (RAG - Milvus)
            // ✅ searchSimilarDiaries 메서드 서명 확인
            log.info("🔍 Milvus에서 관련 일기 검색 중...");
            List<DiaryMemory> relatedDiaries = milvusVectorStore.searchSimilarDiaries(
                    userMessage,
                    userId,
                    petId,
                    3  // Top 3 결과
            );

            log.debug("✅ 관련 일기 {}개 찾음", relatedDiaries.size());

            // Step 2: Context 구성 (일기 + 건강기록)
            log.info("📝 Context 구성 중...");
            String context = buildContextWithDiaries(userId, petId, relatedDiaries);

            // Step 3: 최종 프롬프트 생성
            String fullPrompt = buildFullPrompt(context, userMessage);

            log.debug("📄 생성된 프롬프트 길이: {} 자", fullPrompt.length());

            // Step 4: Claude Sonnet 호출 (✅ invokeClaudeSpecific 사용)
            log.info("🤖 Claude Sonnet 호출 중... (model: {})",
                    bedrockProperties.getModelId());
            String botResponse = claudeClient.invokeClaudeSpecific(
                    bedrockProperties.getModelId(),  // ✅ Sonnet 모델 ID 직접 주입
                    fullPrompt
            );

            // Step 5: Chat History 저장
            log.info("💾 Chat History 저장 중...");
            saveChatHistory(userId, petId, userMessage, botResponse, "PERSONA");

            // Step 6: 관련 일기 ID 리스트 추출
            List<Long> relatedDiaryIds = relatedDiaries.stream()
                    .map(DiaryMemory::getDiaryId)
                    .collect(Collectors.toList());

            log.info("✅ Persona Chat 완료 (관련 일기: {}개)", relatedDiaryIds.size());

            return PersonaChatResponse.of(botResponse, relatedDiaryIds);

        } catch (Exception e) {
            log.error("❌ Persona Chat 중 오류 발생 - userId: {}, petId: {}", userId, petId, e);
            throw new RuntimeException(
                    "페르소나 챗봇 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Context 구성 - 일기, 건강기록 통합
     *
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param relatedDiaries RAG로 검색된 관련 일기
     * @return Context 텍스트
     */
    private String buildContextWithDiaries(Long userId, Long petId,
                                           List<DiaryMemory> relatedDiaries) {
        StringBuilder context = new StringBuilder();

        context.append("=== 반려동물 관련 일기 기록 ===\n");

        // 관련 일기 추가
        if (!relatedDiaries.isEmpty()) {
            for (int i = 0; i < relatedDiaries.size(); i++) {
                DiaryMemory diary = relatedDiaries.get(i);
                context.append(String.format(
                        "[일기 %d] (%s)\n%s\n\n",
                        i + 1,
                        diary.getCreatedAt().toLocalDate(),
                        diary.getContent()
                ));
            }
        } else {
            context.append("(아직 기록된 일기가 없습니다)\n\n");
        }

        // 최근 건강 기록 추가
        context.append("=== 최근 건강 기록 ===\n");
        try {
            // ✅ getWeeklySummary 메서드 서명 확인: (userId: Long, petId: Long)
            String healthSummary = healthRecordService.getWeeklySummary(userId, petId);
            context.append(healthSummary);
        } catch (Exception e) {
            log.warn("⚠️ 건강 기록 조회 실패", e);
            context.append("(건강 기록을 불러올 수 없습니다)\n");
        }

        return context.toString();
    }

    /**
     * 최종 프롬프트 생성
     *
     * @param context 검색된 일기와 건강 기록
     * @param userMessage 사용자 메시지
     * @return Claude에 전달할 최종 프롬프트
     */
    private String buildFullPrompt(String context, String userMessage) {
        return String.format(
                "다음은 반려동물의 기록과 사용자의 질문입니다.\n\n" +
                        "%s\n\n" +
                        "=== 사용자 질문 ===\n" +
                        "%s\n\n" +
                        "위의 기록을 참고하여 따뜻하고 도움이 되는 답변을 해주세요.",
                context,
                userMessage
        );
    }

    /**
     * Chat History 저장
     *
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param userMessage 사용자 메시지
     * @param botResponse 봇 응답
     * @param chatType 채팅 타입 ("PERSONA" 고정)
     */
    @Transactional
    private void saveChatHistory(Long userId, Long petId, String userMessage,
                                 String botResponse, String chatType) {
        try {
            // ✅ ChatHistory.builder() 사용 (Rich Domain Model - Setter 없음)
            ChatHistory history = ChatHistory.builder()
                    .userId(userId)
                    .petId(petId)
                    .chatType(chatType)  // ✅ "PERSONA" 고정
                    .userMessage(userMessage)
                    .botResponse(botResponse)
                    .responseTimeMs((int) (Math.random() * 1000))  // Mock 처리
                    .createdAt(LocalDateTime.now())
                    .build();

            // ✅ ChatHistoryRepository.save() 호출
            chatHistoryRepository.save(history);
            log.debug("✅ Chat history 저장 완료 - userId: {}", userId);

        } catch (Exception e) {
            log.error("❌ Chat history 저장 실패", e);
            // Chat History 저장 실패는 사용자 응답에 영향을 주지 않음
        }
    }

    /**
     * 유틸리티: 텍스트 자르기 (로그용)
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }
}