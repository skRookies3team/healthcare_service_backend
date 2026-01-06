package com.petlog.healthcare.service;

import com.petlog.healthcare.client.UserServiceClient;
import com.petlog.healthcare.client.dto.PetInfoResponse;
import com.petlog.healthcare.infrastructure.bedrock.ClaudeClient;
import com.petlog.healthcare.infrastructure.milvus.MilvusSearchService;
import com.petlog.healthcare.infrastructure.bedrock.TitanEmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

/**
 * Pet Persona Chatbot Service
 *
 * [핵심 기능]
 * - 반려동물이 직접 대화하는 듯한 페르소나 챗봇
 * - Diary 벡터를 활용한 "기억" 기반 대화
 * - Pet 정보(품종, 나이, 성격) 반영
 *
 * @author healthcare-team
 * @since 2025-01-02
 * @version 1.1 (UserServiceClient 연동)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetPersonaService {

    private final ClaudeClient claudeClient;
    private final TitanEmbeddingClient titanEmbeddingClient;
    private final MilvusSearchService milvusSearchService;
    private final UserServiceClient userServiceClient;

    /**
     * Pet Persona 대화 (인증 포함)
     *
     * @param petId         반려동물 ID
     * @param userMessage   사용자 메시지
     * @param authorization JWT 토큰
     * @return 페르소나 응답 (1인칭 화법)
     */
    public String chat(Long petId, String userMessage, String authorization) {
        log.info("🐾 Pet Persona 대화 시작 - petId: {}, message: '{}'", petId, userMessage);

        try {
            // Step 1: User Service에서 Pet 정보 조회
            PetInfoResponse petInfo = fetchPetInfo(petId, authorization);
            String petName = petInfo.getPetName();
            String petSpecies = getSpeciesKorean(petInfo.getSpecies());
            String breed = petInfo.getBreed() != null ? petInfo.getBreed() : petSpecies;
            int petAge = calculateAge(petInfo.getBirth());

            log.info("✅ Pet 정보 조회: name={}, species={}, age={}", petName, petSpecies, petAge);

            // Step 2: Diary 벡터 검색 (과거 기억)
            String diaryContext = searchDiaryMemories(petId, userMessage);

            // Step 3: Persona Prompt 생성
            String prompt = buildPersonaPrompt(petName, breed, petAge, diaryContext, userMessage);

            log.debug("📝 Persona Prompt 길이: {} 자", prompt.length());

            // Step 4: Claude 호출
            String response = claudeClient.invokeClaude(prompt);

            log.info("✅ Pet Persona 응답 완료");
            return response;

        } catch (Exception e) {
            log.error("❌ Pet Persona 대화 실패", e);
            return "멍... 무슨 말인지 잘 모르겠어 🐶 (오류 발생)";
        }
    }

    /**
     * Pet Persona 대화 (인증 없음 - 테스트용)
     */
    public String chat(Long petId, String userMessage) {
        return chat(petId, userMessage, null);
    }

    /**
     * Pet 정보 조회
     */
    private PetInfoResponse fetchPetInfo(Long petId, String authorization) {
        if (authorization != null && !authorization.isBlank()) {
            try {
                return userServiceClient.getPetInfo(petId, authorization);
            } catch (Exception e) {
                log.warn("⚠️ User Service 연결 실패, Mock 데이터 사용: {}", e.getMessage());
            }
        }

        // Fallback: Mock 데이터
        return PetInfoResponse.builder()
                .petId(petId)
                .petName("몽치")
                .species("DOG")
                .breed("골든리트리버")
                .birth(LocalDate.now().minusYears(3))
                .build();
    }

    /**
     * 나이 계산
     */
    private int calculateAge(LocalDate birth) {
        if (birth == null)
            return 3;
        return Period.between(birth, LocalDate.now()).getYears();
    }

    /**
     * 종류 한글 변환
     */
    private String getSpeciesKorean(String species) {
        if (species == null)
            return "반려동물";
        return switch (species.toUpperCase()) {
            case "DOG" -> "강아지";
            case "CAT" -> "고양이";
            case "BIRD" -> "새";
            case "FISH" -> "물고기";
            default -> "반려동물";
        };
    }

    /**
     * Diary 벡터 검색 (과거 기억)
     */
    private String searchDiaryMemories(Long petId, String query) {
        try {
            float[] queryEmbedding = titanEmbeddingClient.generateEmbedding(query);
            List<MilvusSearchService.SearchResult> results = milvusSearchService.search(queryEmbedding, petId, 3);

            if (results.isEmpty()) {
                return "아직 기억이 별로 없어.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("내 기억 속 이야기들:\n\n");

            for (int i = 0; i < results.size(); i++) {
                MilvusSearchService.SearchResult result = results.get(i);
                sb.append(String.format("%d. %s (날짜: %s)\n",
                        i + 1, result.getContent(), result.getCreatedAt()));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("❌ Diary 검색 실패", e);
            return "기억이 잘 안 나...";
        }
    }

    /**
     * Persona Prompt 생성
     */
    private String buildPersonaPrompt(String petName, String breed, int petAge,
            String diaryContext, String userMessage) {
        return String.format("""
                당신은 "%s"라는 이름의 %d살 %s입니다.
                주인을 무척 사랑하고, 순수하고 감성적인 성격입니다.

                ## 말투 규칙
                - 1인칭 화법 사용 ("나", "내가", "나는")
                - 친근하고 귀여운 톤 ("~했어!", "~할래!", "~멍!" 등)
                - 이모지 적절히 사용 (🐾, 🐶, ✨, ❤️)

                ## 대화 가이드
                1. 과거 기억(일기)을 자연스럽게 언급하세요
                2. 감정을 솔직하게 표현하세요
                3. 주인에게 궁금한 것도 물어보세요
                4. 3-4문장 정도로 간결하게 답변하세요

                ## 내 기억 (과거 일기)
                %s

                ## 주인이 말한 것
                "%s"

                ## 답변 (반려동물 말투로)
                """,
                petName, petAge, breed,
                diaryContext,
                userMessage);
    }
}