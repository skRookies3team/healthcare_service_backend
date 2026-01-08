package com.petlog.healthcare.service;

import com.petlog.healthcare.config.BedrockConfig;
import com.petlog.healthcare.infrastructure.bedrock.ClaudeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Claude Service (SimpleFileRag + Dual Models 통합)
 *
 * 📌 3가지 챗봇 모드:
 * 1️⃣ chat() - 일반 수의사 모드 (전반적인 건강 조언)
 * 2️⃣ chatHaiku() - 빠른 팁 (Haiku, RAG 없음)
 * 3️⃣ chatPersona() - 펫이 직접 말하는 방식 (펫의 관점에서 자신의 건강 상태 표현)
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeService {

    private final ClaudeClient claudeClient;
    private final SimpleFileRagService ragService;
    private final BedrockConfig.BedrockProperties bedrockProperties;

    /**
     * 1️⃣ 일반 챗봇: 수의사 느낌 (Sonnet + RAG)
     * 사용자: 강아지가 기침을 하는데 뭘해야돼?
     * 응답: "일반적으로 강아지의 기침은... 라이펫 자료에 따르면..."
     */
    public String chat(String message) {
        log.info("💬 [일반 챗봇] 수의사 모드 처리: {}", truncate(message, 50));

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("메시지가 비어있습니다.");
        }

        try {
            // Step 1: 일반적인 건강 정보 RAG 검색
            log.info("🔍 라이펫 건강 정보 검색 중...");
            String ragContext = ragService.search(message);

            // Step 2: 수의사 느낌 프롬프트
            String prompt = buildGeneralVetPrompt(ragContext, message);

            // Step 3: Sonnet으로 호출
            log.info("🤖 Claude Sonnet (수의사 모드) 호출 중...");
            String response = claudeClient.invokeClaude(prompt);

            log.info("✅ 일반 챗봇 처리 완료");
            return response;

        } catch (Exception e) {
            log.error("❌ 일반 챗봇 처리 실패", e);
            throw new RuntimeException("채팅 처리 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * 2️⃣ 빠른 팁: Haiku 빠른 응답 (RAG 없음)
     * 사용자: 강아지 귀 청소는 자주 해야돼?
     * 응답: "일반적으로 주 1-2회... (빠르고 간단함)"
     */
    public String chatHaiku(String message) {
        log.info("⚡ [빠른 팁] Haiku 모드 처리: {}", truncate(message, 50));

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("메시지가 비어있습니다.");
        }

        try {
            String prompt = buildQuickTipPrompt(message);

            log.info("⚡ Claude Haiku (빠른 팁) 호출 중...");
            String response = claudeClient.invokeClaudeSpecific(
                    bedrockProperties.getHaikuModelId(),
                    prompt
            );

            log.info("✅ 빠른 팁 처리 완료");
            return response;

        } catch (Exception e) {
            log.error("❌ 빠른 팁 처리 실패", e);
            throw new RuntimeException("빠른 팁 처리 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * 3️⃣ 페르소나 챗봇: 펫이 직접 말하는 방식
     *
     * 핵심: 펫이 주인공이 되어 자신의 건강 상태를 직접 표현
     *
     * 📍 요청 구조:
     * {
     *   "message": "요즘 자꾸 배가 아파",
     *   "petId": "pet_123",
     *   "petProfile": {
     *     "name": "뽀삐",
     *     "breed": "말티즈",
     *     "age": 3,
     *     "weight": 3.5
     *   },
     *   "healthHistory": "2025-01: 정장염",
     *   "recentDiary": "요즘 밥을 덜 먹어",
     *   "emotion": "sad",
     *   "date": "2026-01-02"
     * }
     *
     * 응답 예시 (펫이 직접 말함):
     * "멍~ 내 배가 자꾸 아파... 엄마가 알아줄 수 있으면 좋겠어.
     *  지난 1월에도 배 때문에 고생했었는데... 또 그런 건가?
     *  요즘 밥도 잘 못 먹고 있어서 더 약해진 것 같아.
     *  병원에 가봐야 할 것 같은데, 엄마 도와줄래?"
     */
    public String chatPersona(String message, String petId, String petProfile,
                              String healthHistory, String recentDiary,
                              String emotion, String date) {
        log.info("🧠 [페르소나 챗봇] 펫의 입장에서 직접 대답: {}", truncate(message, 50));
        log.info("   📍 펫 ID: {}, 날짜: {}, 기분: {}", petId, date, emotion);

        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("메시지가 비어있습니다.");
        }

        try {
            // Step 1: 펫의 건강 정보 + 일기 + 감정 컨텍스트 구성
            log.info("🔍 펫의 건강 기록 및 감정 상태 분석 중... (펫ID: {})", petId);
            String petContextualInfo = buildPetContext(
                    petProfile,
                    healthHistory,
                    recentDiary,
                    emotion,
                    date,
                    message
            );

            // Step 2: 펫이 직접 말하는 프롬프트
            String prompt = buildPetDirectSpeechPrompt(petContextualInfo, message, petProfile);

            // Step 3: Sonnet으로 호출
            log.info("🧠 Claude Sonnet (펫의 직접 표현) 호출 중...");
            String response = claudeClient.invokeClaudeSpecific(
                    bedrockProperties.getModelId(),
                    prompt
            );

            log.info("✅ 페르소나 챗봇 처리 완료 (펫ID: {})", petId);
            return response;

        } catch (Exception e) {
            log.error("❌ 페르소나 챗봇 처리 실패", e);
            throw new RuntimeException("페르소나 채팅 처리 중 오류: " + e.getMessage(), e);
        }
    }

    /**
     * 오버로드: petProfile을 Map으로 받는 버전 (JSON 호환)
     */
    public String chatPersona(String message, String petId,
                              java.util.Map<String, Object> petProfile,
                              String healthHistory, String recentDiary,
                              String emotion, String date) {
        String petProfileStr = formatPetProfile(petProfile);
        return chatPersona(message, petId, petProfileStr, healthHistory, recentDiary, emotion, date);
    }

    /**
     * 펫 프로필을 문자열로 변환 (Map → String)
     */
    private String formatPetProfile(java.util.Map<String, Object> petProfile) {
        if (petProfile == null || petProfile.isEmpty()) {
            return "펫 정보 없음";
        }

        StringBuilder sb = new StringBuilder();
        petProfile.forEach((key, value) -> {
            if (value != null) {
                sb.append(String.format("- %s: %s%n", key, value));
            }
        });
        return sb.toString();
    }

    /**
     * 펫의 실제 컨텍스트 정보 구성
     * (건강기록 + 일기 + 감정 + 날짜 통합)
     */
    private String buildPetContext(String petProfile, String healthHistory,
                                   String recentDiary, String emotion,
                                   String date, String userMessage) {
        return String.format("""
            🐾 내 정보 (나는 이런 펫이야)
            %s
            
            📅 오늘
            - 날짜: %s
            - 내 기분: %s (엄마/아빠가 관찰한)
            
            💊 내가 겪었던 건강 문제들
            %s
            
            📔 내가 최근에 보인 행동들
            %s
            
            💭 엄마/아빠가 오늘 해준 말
            "%s"
            """,
                petProfile,
                date,
                emotion,
                healthHistory.isEmpty() ? "특별한 건강 문제는 없어" : healthHistory,
                recentDiary.isEmpty() ? "특별한 변화는 없어" : recentDiary,
                userMessage
        );
    }

    /**
     * 1️⃣ 일반 수의사 프롬프트
     * 톤: 전문적이고 친절한 수의사
     */
    private String buildGeneralVetPrompt(String ragContext, String userMessage) {
        return String.format("""
            당신은 반려동물 건강 전문가(수의사)입니다.
            
            ## 역할
            - 반려동물 일반 건강 상담 제공
            - 증상 분석 및 조치 방법 안내 (일반론)
            - 병원 방문이 필요한 경우 명확히 권고
            
            ## 참고 자료 (라이펫 건강 정보)
            %s
            
            ## 사용자 질문
            %s
            
            ## 답변 가이드라인
            1. **톤**: 전문적이고 신뢰할 수 있는 수의사 톤
            2. **출처 명시**: "일반적으로..." 또는 "라이펫 자료에 따르면..."
            3. **의료 안전**: 
               - 확실하지 않은 진단 금지
               - 약물 처방 절대 금지
               - 응급 증상은 즉시 병원 방문 강조
            4. **구조**: 증상 분석 → 원인 → 조치 방법 → 병원 필요 여부
            
            답변을 시작하세요:
            """,
                ragContext,
                userMessage
        );
    }

    /**
     * 2️⃣ 빠른 팁 프롬프트 (Haiku용)
     * 톤: 간단하고 직관적
     */
    private String buildQuickTipPrompt(String userMessage) {
        return String.format("""
            당신은 반려동물 건강 전문가입니다.
            간단하고 빠르게 실용적인 팁을 제공하세요.
            
            ## 사용자 질문
            %s
            
            ## 답변 형식
            - 핵심 조언 (3줄 이내)
            - 병원 필요 여부 명확히
            - 응급이면 ⚠️ 표시
            
            답변을 시작하세요:
            """, userMessage);
    }

    /**
     * 3️⃣ 페르소나 프롬프트 - 펫이 직접 말하는 방식
     *
     * ⭐ 핵심: "나(펫)가 직접 말한다"
     * 톤: 애교 있고, 걱정스럽고, 엄마/아빠에게 호소하는 듯한 느낌
     *
     * 예시:
     * "멍~ 내 배가 자꾸 아파... 엄마 도와줄래?
     *  지난 1월에도 이런 일이 있었는데, 또 그런 건가봐...
     *  요즘 밥도 덜 먹고 있잖아. 더 약해진 건 아닐까?
     *  병원에 가봐야 할 것 같은데... 엄마 도와줘!"
     */
    private String buildPetDirectSpeechPrompt(String petContext, String userMessage,
                                              String petProfile) {
        return String.format("""
            🐾 당신은 이 반려동물입니다. 당신이 직접 말합니다.
            
            당신은 당신의 건강 상태, 감정, 불안함을 **직접** 엄마/아빠에게 호소하고 있습니다.
            
            %s
            
            ## 당신의 말투
            ✨ 가능한 톤 (펫의 울음소리로 시작):
            - "멍~", "냐옹~", "짹짹~" 등 펫의 울음소리
            - 상황에 맞춰 애교 있고, 걱정스럽고, 신뢰하는 듯한 톤
            - 엄마/아빠에게 직접 호소하는 느낌
            - "내", "나", "내가" 등 1인칭 사용
            - 자신의 감정과 불편함을 솔직하게 표현
            
            ## 당신이 포함해야 할 것들
            1. **나의 건강 문제**: "내 배가 아파", "요즘 기침이 나" 등
            2. **과거 경험 언급**: "지난 1월에도 이런 일이 있었는데..."
            3. **최근 행동 변화**: "요즘 밥도 덜 먹고 있어" 등
            4. **현재 감정 상태**: 불안함, 걱정, 불편함 표현
            5. **도움 요청**: "병원에 가봐야 할 것 같아", "도와줄래?" 등
            
            ## 의료 안전 가이드
            - 확실하지 않은 진단 금지
            - 약물 이름 절대 금지
            - 응급 증상 느껴지면 "병원에 가자" 표현
            - 불안함과 신뢰 섞인 표현으로 자연스럽게
            
            ## 예시 (참고만 하세요)
            "멍~ 내 배가 요즘 자꾸 아파서... 
             엄마가 알아줄 수 있으면 좋겠어.
             지난 1월에도 이런 일이 있었잖아...
             또 그런 건가 봐.
             요즘 밥도 잘 못 먹고 있어서 더 약해진 것 같아.
             병원에 가봐야 할 것 같은데, 엄마 도와줄래?"
            
            이제 엄마/아빠에게 당신의 상태를 직접 말해주세요:
            """,
                petContext
        );
    }

    /**
     * 유틸리티: 텍스트 자르기 (로그용)
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}