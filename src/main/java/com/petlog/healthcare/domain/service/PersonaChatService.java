package com.petlog.healthcare.domain.service;

import com.petlog.healthcare.api.dto.response.PersonaChatResponse;
import com.petlog.healthcare.config.BedrockConfig.BedrockProperties;
import com.petlog.healthcare.entity.ChatHistory;
import com.petlog.healthcare.domain.entity.DiaryMemory;
import com.petlog.healthcare.domain.repository.ChatHistoryRepository;
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
 * ✅ 완벽 구현된 Persona Chat Service (모든 오류 해결)
 *
 * WHY? RAG + DDD + 현직 수준 코드
 * - Milvus 벡터 검색으로 사용자 맥락 이해
 * - Claude Sonnet과 통합하여 개인화된 답변
 * - Rich Domain Model (Setter 없음) 패턴
 * - Transactional 최소화 (클래스 레벨만)
 *
 * Architecture:
 * 1. 사용자 메시지 벡터화 & Milvus 검색
 * 2. 관련 일기 Top 3 + 건강기록 Context
 * 3. Claude Sonnet 호출 (RAG Context 포함)
 * 4. Chat History 저장 (응답시간 추적)
 *
 * @author healthcare-team
 * @since 2026-01-02
 * @version 2.1 (모든 오류 해결)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)  // ✅ 클래스 레벨에서만 사용
public class PersonaChatService {

    // ✅ 의존성 주입 (DI) - 변수명은 소문자로 시작
    private final ClaudeClient claudeClient;
    private final MilvusVectorStore milvusVectorStore;  // ✅ milvusVectorStore (오타 수정)
    private final ChatHistoryRepository chatHistoryRepository;
    private final HealthRecordService healthRecordService;
    private final BedrockProperties bedrockProperties;

    // ✅ RAG 설정값
    private static final int TOP_K = 3;  // Top 3 관련 일기
    private static final double MIN_SCORE = 0.65;

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
     * ✅ Persona Chat 실행 (RAG 기반)
     *
     * Flow:
     * 1. 사용자 메시지 → Milvus 벡터 검색
     * 2. 관련 일기 Top 3 + 건강기록 Context
     * 3. Claude Sonnet 호출 (Context 포함)
     * 4. Chat History 저장
     *
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param userMessage 사용자 메시지
     * @return PersonaChatResponse (봇 응답 + 관련 일기 ID)
     */
    @Transactional  // ✅ 메서드 레벨에서만 추가 (write operation)
    public PersonaChatResponse chat(Long userId, Long petId, String userMessage) {
        log.info("🧠 [Persona Chat] userId: {}, petId: {}, message: {}",
                userId, petId, truncate(userMessage, 50));

        try {
            // Step 1: Milvus RAG 검색 (기존 메서드 사용)
            log.info("🔍 Milvus 벡터 검색 시작 (Top {})", TOP_K);
            List<DiaryMemory> relatedDiaries = milvusVectorStore.searchSimilarDiaries(
                    userMessage,
                    userId,
                    petId,
                    TOP_K  // ✅ searchSimilarDiaries 메서드 (searchWithReranking 아님)
            );

            log.info("✅ 관련 일기 {}개 찾음", relatedDiaries.size());

            // Step 2: Context 구성 (일기 + 건강기록)
            log.info("📝 Enhanced Context 구성 중...");
            String context = buildEnhancedContext(userId, petId, relatedDiaries);

            // Step 3: 최종 프롬프트 생성 (System Prompt 포함)
            String fullPrompt = buildFullPrompt(context, userMessage);

            log.debug("📄 생성된 프롬프트 길이: {} 자", fullPrompt.length());

            // Step 4: Claude Sonnet 호출
            log.info("🤖 Claude Sonnet 호출 중... (model: {})",
                    bedrockProperties.getModelId());

            long startTime = System.currentTimeMillis();
            String botResponse = claudeClient.invokeClaudeSpecific(
                    bedrockProperties.getModelId(),
                    fullPrompt
            );
            long responseTime = System.currentTimeMillis() - startTime;

            log.debug("📤 Claude 응답 길이: {} 자, 응답시간: {}ms",
                    botResponse.length(), responseTime);

            // Step 5: Chat History 저장
            log.info("💾 Chat History 저장 중...");
            saveChatHistory(userId, petId, userMessage, botResponse,
                    "PERSONA", (int) responseTime);  // ✅ chatType = "PERSONA" 고정

            // Step 6: 관련 일기 ID 리스트 추출
            List<Long> relatedDiaryIds = relatedDiaries.stream()
                    .map(DiaryMemory::getDiaryId)
                    .collect(Collectors.toList());

            log.info("✅ Persona Chat 완료 (관련 일기: {}개)", relatedDiaryIds.size());

            return PersonaChatResponse.of(botResponse, relatedDiaryIds);

        } catch (Exception e) {
            log.error("❌ Persona Chat 중 오류 발생 - userId: {}, petId: {}",
                    userId, petId, e);
            throw new RuntimeException(
                    "페르소나 챗봇 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * Enhanced Context 구성
     *
     * WHY? Milvus 검색 결과 + 건강기록을 결합하여
     * Claude가 사용자의 펫에 대한 맥락을 완벽히 이해
     *
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param relatedDiaries RAG 검색 결과 (Top 3)
     * @return Context 텍스트 (일기 + 건강기록)
     */
    private String buildEnhancedContext(
            Long userId,
            Long petId,
            List<DiaryMemory> relatedDiaries
    ) {
        StringBuilder context = new StringBuilder();

        context.append("=== 🐾 반려동물 관련 일기 기록 (RAG 검색 결과) ===\n\n");

        // 관련 일기 추가 (Top 3)
        if (!relatedDiaries.isEmpty()) {
            for (int i = 0; i < relatedDiaries.size(); i++) {
                DiaryMemory diary = relatedDiaries.get(i);
                context.append(String.format(
                        "[일기 %d] 📅 %s\n%s\n\n",
                        i + 1,
                        diary.getCreatedAt().toLocalDate(),
                        diary.getContent()
                ));
            }
        } else {
            context.append("(아직 기록된 일기가 없습니다)\n\n");
        }

        // 최근 건강 기록 추가
        context.append("=== 🏥 최근 건강 기록 (주간 요약) ===\n");
        try {
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
     * WHY? System Prompt + Context + User Message를 결합하여
     * Claude가 다양한 정보를 바탕으로 최적의 답변 생성
     *
     * @param context Milvus RAG 검색 결과 + 건강기록
     * @param userMessage 사용자 메시지
     * @return Claude에 전달할 최종 프롬프트
     */
    private String buildFullPrompt(String context, String userMessage) {
        return String.format(
                "%s\n\n" +
                        "다음은 반려동물의 기록과 사용자의 질문입니다.\n\n" +
                        "%s\n\n" +
                        "=== 💬 사용자 질문 ===\n" +
                        "%s\n\n" +
                        "위의 기록을 참고하여 따뜻하고 도움이 되는 답변을 해주세요.",
                PERSONA_SYSTEM_PROMPT,
                context,
                userMessage
        );
    }

    /**
     * Chat History 저장 (DDD Rich Domain Model)
     *
     * WHY? 모든 채팅을 기록하여:
     * - 사용자 경험 개선 (대화 이력 유지)
     * - 모델 성능 분석 (응답시간, 품질)
     * - 향후 Fine-tuning 데이터 수집
     *
     * @param userId 사용자 ID
     * @param petId 반려동물 ID
     * @param userMessage 사용자 메시지
     * @param botResponse 봇 응답
     * @param chatType 채팅 타입 ("PERSONA" 고정)
     * @param responseTimeMs 응답시간 (ms)
     */
      // ✅ DB 저장이므로 별도 Transactional 필요
    private void saveChatHistory(
            Long userId,
            Long petId,
            String userMessage,
            String botResponse,
            String chatType,
            Integer responseTimeMs
    ) {
        try {
            // ✅ ChatHistory.builder() 사용 (Rich Domain Model)
            ChatHistory history = ChatHistory.builder()
                    .userId(userId)
                    .petId(petId)
                    .chatType(chatType)  // ✅ "PERSONA" 고정
                    .userMessage(userMessage)
                    .botResponse(botResponse)
                    .responseTimeMs(responseTimeMs)
                    .createdAt(LocalDateTime.now())
                    .build();

            // ✅ Repository.save() 호출
            chatHistoryRepository.save(history);
            log.debug("✅ Chat history 저장 완료 - userId: {}, chatType: {}",
                    userId, chatType);

        } catch (Exception e) {
            log.error("❌ Chat history 저장 실패", e);
            // Chat History 저장 실패는 사용자 응답에 영향을 주지 않음
            // (graceful degradation)
        }
    }

    /**
     * 유틸리티: 텍스트 자르기 (로그용)
     *
     * WHY? 로그에서 너무 긴 텍스트를 표시하지 않기 위해
     *
     * @param text 원본 텍스트
     * @param maxLength 최대 길이
     * @return 자른 텍스트
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";  // ✅ maxLength = 50
    }
}