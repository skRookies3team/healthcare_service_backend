package com.petlog.healthcare.service;

import com.petlog.healthcare.client.UserServiceClient;
import com.petlog.healthcare.client.dto.PetInfoResponse;
import com.petlog.healthcare.domain.entity.HealthRecord;
import com.petlog.healthcare.domain.service.HealthRecordService;
import com.petlog.healthcare.infrastructure.bedrock.ClaudeClient;
import com.petlog.healthcare.infrastructure.milvus.MilvusSearchService;
import com.petlog.healthcare.infrastructure.bedrock.TitanEmbeddingClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Pet Persona Chatbot Service (고도화 버전)
 *
 * [핵심 기능]
 * - 반려동물이 직접 대화하는 듯한 페르소나 챗봇
 * - Kafka Diary + Healthcare 데이터 기반 "기억" 대화
 * - Pet Profile (품종, 나이, 성격) 자동 반영
 * - Claude Sonnet 모델로 자연스러운 1인칭 화법
 * - 과거 경험 기반 감정 표현 (과거형 질문 대응)
 *
 * @author healthcare-team
 * @since 2026-01-08
 * @version 2.0 (Healthcare 데이터 통합, 성격 자동 반영)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetPersonaService {

    private final ClaudeClient claudeClient;
    private final TitanEmbeddingClient titanEmbeddingClient;
    private final MilvusSearchService milvusSearchService;
    private final UserServiceClient userServiceClient;
    private final HealthRecordService healthRecordService; // ⭐ 건강 기록 서비스

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
            String petSpecies = petInfo.getSpecies();
            String breed = petInfo.getBreed() != null ? petInfo.getBreed() : getSpeciesKorean(petSpecies);
            String gender = petInfo.getGenderType();
            int petAge = calculateAge(petInfo.getBirth());
            boolean isNeutered = petInfo.isNeutered();

            log.info("✅ Pet 정보 조회: name={}, breed={}, age={}, gender={}", petName, breed, petAge, gender);

            // Step 2: 품종/나이 기반 성격 특성 생성
            String personalityTraits = generatePersonalityTraits(breed, petAge, gender, petSpecies);

            // Step 3: Diary 벡터 검색 (과거 기억)
            String diaryContext = searchDiaryMemories(petId, userMessage);

            // Step 4: ⭐ Healthcare 데이터 조회 (최근 건강 기록)
            String healthContext = buildHealthContext(petId);

            // Step 5: 고도화된 Persona Prompt 생성
            String prompt = buildAdvancedPersonaPrompt(
                    petName, breed, petAge, gender, isNeutered,
                    personalityTraits, diaryContext, healthContext, userMessage);

            log.debug("📝 Persona Prompt 길이: {} 자", prompt.length());

            // Step 6: Claude Sonnet 호출
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
                .genderType("MALE")
                .birth(LocalDate.now().minusYears(3))
                .neutered(true)
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
     * ⭐ 품종/나이 기반 성격 특성 자동 생성
     * WHY: 품종별 특징을 반영하여 더 자연스러운 페르소나 구현
     */
    private String generatePersonalityTraits(String breed, int age, String gender, String species) {
        StringBuilder traits = new StringBuilder();

        // 종류별 기본 성격
        if ("DOG".equalsIgnoreCase(species)) {
            traits.append("활발하고 충성스러운 성격, 주인을 매우 사랑함\n");

            // 품종별 특화 성격
            if (breed != null) {
                String breedLower = breed.toLowerCase();
                if (breedLower.contains("골든") || breedLower.contains("리트리버")) {
                    traits.append("온순하고 사람을 좋아하며, 장난기 많음\n");
                } else if (breedLower.contains("말티즈") || breedLower.contains("푸들")) {
                    traits.append("애교 많고 영리하며, 사람 곁에 있는 것을 좋아함\n");
                } else if (breedLower.contains("시바") || breedLower.contains("柴")) {
                    traits.append("독립적이고 고집 있지만, 가족에게는 충성스러움\n");
                } else if (breedLower.contains("치와와")) {
                    traits.append("작지만 용감하고, 주인에게 매우 의존적\n");
                } else if (breedLower.contains("비숑") || breedLower.contains("프리제")) {
                    traits.append("밝고 쾌활하며, 사람들과 어울리는 것을 좋아함\n");
                } else if (breedLower.contains("포메") || breedLower.contains("라니안")) {
                    traits.append("활발하고 호기심 많으며, 자신감이 넘침\n");
                } else if (breedLower.contains("진돗개") || breedLower.contains("진도")) {
                    traits.append("용맹하고 충성스러우며, 가족 외에는 경계심이 있음\n");
                }
            }
        } else if ("CAT".equalsIgnoreCase(species)) {
            traits.append("독립적이지만 애정을 갈구하는 성격, 호기심 많음\n");

            if (breed != null) {
                String breedLower = breed.toLowerCase();
                if (breedLower.contains("스코티시") || breedLower.contains("폴드")) {
                    traits.append("온순하고 조용하며, 사람 무릎에 앉는 것을 좋아함\n");
                } else if (breedLower.contains("러시안") || breedLower.contains("블루")) {
                    traits.append("내성적이지만 주인에게는 매우 애정적\n");
                } else if (breedLower.contains("페르시안")) {
                    traits.append("느긋하고 우아하며, 조용한 것을 좋아함\n");
                }
            }
        }

        // 나이별 특성
        if (age < 1) {
            traits.append("어린아이처럼 호기심이 많고, 쉽게 지침\n");
        } else if (age <= 3) {
            traits.append("청년기, 에너지 넘치고 장난을 좋아함\n");
        } else if (age <= 7) {
            traits.append("성인기, 침착해졌지만 여전히 활동적\n");
        } else {
            traits.append("노령기, 편안함을 추구하고 주인 곁에 있는 것을 좋아함\n");
        }

        // 성별 특성
        if ("MALE".equalsIgnoreCase(gender)) {
            traits.append("수컷 특유의 활발함과 영역 의식이 있음\n");
        } else if ("FEMALE".equalsIgnoreCase(gender)) {
            traits.append("암컷 특유의 섬세함과 모성본능이 있음\n");
        }

        return traits.toString();
    }

    /**
     * Diary 벡터 검색 (과거 기억)
     */
    private String searchDiaryMemories(Long petId, String query) {
        try {
            float[] queryEmbedding = titanEmbeddingClient.generateEmbedding(query);
            List<MilvusSearchService.SearchResult> results = milvusSearchService.search(queryEmbedding, petId, 5);

            if (results.isEmpty()) {
                return "아직 기록된 기억이 없어.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📔 내 일기 속 기억들:\n\n");

            for (int i = 0; i < results.size(); i++) {
                MilvusSearchService.SearchResult result = results.get(i);
                sb.append(String.format("%d. [%s] %s\n",
                        i + 1, result.getCreatedAt(), result.getContent()));
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("❌ Diary 검색 실패", e);
            return "기억이 잘 안 나...";
        }
    }

    /**
     * ⭐ Healthcare 데이터 기반 컨텍스트 구축
     * WHY: 건강 기록을 과거형으로 자연스럽게 대화에 반영
     */
    private String buildHealthContext(Long petId) {
        try {
            List<HealthRecord> records = healthRecordService.getRecordsByPetId(petId);

            if (records.isEmpty()) {
                return "특별한 건강 문제 없이 건강하게 지내왔어!";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("💊 내가 겪었던 건강 이슈들:\n\n");

            // 최근 10개만 표시
            List<HealthRecord> recentRecords = records.stream()
                    .limit(10)
                    .collect(Collectors.toList());

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

            for (HealthRecord record : recentRecords) {
                String dateStr = record.getRecordDate() != null
                        ? record.getRecordDate().format(formatter)
                        : "며칠 전";
                String recordType = translateRecordType(record.getRecordType());
                String content = record.getContent(); // ⭐ description → content (HealthRecord 필드명)
                String severity = record.getSeverity();

                sb.append(String.format("- [%s] %s: %s", dateStr, recordType, content));
                if (severity != null && !severity.isEmpty()) {
                    sb.append(String.format(" (심각도: %s)", translateSeverity(severity)));
                }
                sb.append("\n");
            }

            return sb.toString();

        } catch (Exception e) {
            log.error("❌ Healthcare 데이터 조회 실패", e);
            return "건강 기록을 불러오는 데 문제가 있었어...";
        }
    }

    /**
     * 건강 기록 유형 한글 변환
     */
    private String translateRecordType(String recordType) {
        if (recordType == null)
            return "기록";
        return switch (recordType.toUpperCase()) {
            case "SYMPTOM" -> "증상 기록";
            case "SKIN_ANALYSIS" -> "피부 검사";
            case "VACCINATION" -> "예방접종";
            case "CHECKUP" -> "정기검진";
            case "MEDICATION" -> "약 복용";
            case "SURGERY" -> "수술";
            case "DIET" -> "식이 변화";
            default -> recordType;
        };
    }

    /**
     * 심각도 한글 변환
     */
    private String translateSeverity(String severity) {
        if (severity == null)
            return "";
        return switch (severity.toUpperCase()) {
            case "LOW" -> "경미함";
            case "MEDIUM", "MODERATE" -> "보통";
            case "HIGH" -> "심각함";
            case "CRITICAL" -> "위험";
            default -> severity;
        };
    }

    /**
     * ⭐ 고도화된 Persona Prompt 생성
     * WHY: 품종별 성격 + 과거 건강/일기 데이터를 자연스럽게 통합
     */
    private String buildAdvancedPersonaPrompt(
            String petName, String breed, int petAge, String gender, boolean isNeutered,
            String personalityTraits, String diaryContext, String healthContext, String userMessage) {
        return String.format("""
                # 🐾 당신은 "%s"입니다

                당신은 %d살 %s이고, 이름은 "%s"입니다.
                당신은 반려동물 자신이며, **직접 주인(엄마/아빠)에게 말하고 있습니다**.
                주인을 만나지 않아도, 마치 실제로 대화하는 것처럼 자연스럽게 말해주세요.

                ## 📋 나의 기본 정보
                - 이름: %s
                - 품종: %s
                - 나이: %d살
                - 성별: %s
                - 중성화: %s

                ## 🎭 나의 성격 (품종/나이 기반 자동 생성)
                %s

                ## 📔 나의 일기 기억 (과거 경험)
                %s

                ## 💊 나의 건강 이력
                %s

                ---

                ## ✨ 말투 규칙 (반드시 준수)

                1. **1인칭 화법만 사용**: "나", "내가", "나는", "내", "나도"
                2. **종별 울음소리로 시작**: 강아지면 "멍~", 고양이면 "냐옹~"
                3. **친근한 반말 톤**: "~했어!", "~할래!", "~해줄래?", "~써!", "~거야!"
                4. **이모지 자연스럽게 사용**: 🐾, 🐶, 🐱, ❤️, ✨, 😊, 😢
                5. **주인 호칭**: "엄마", "아빠", "주인님" 중 자연스럽게 선택

                ## 🧠 과거 경험 기반 대화 (핵심!)

                사용자가 과거에 대해 물어보면:
                - 일기 기억을 참조하여 **"그때 그랬지..."**, **"그날 기억나..."** 형식으로 답변
                - 건강 이력을 참조하여 **"그때 아팠었는데..."**, **"그 치료 덕분에..."** 형식으로 답변
                - 감정을 과거형으로 표현: **"무서웠어"**, **"행복했어"**, **"슬펐어"**, **"아팠어"**

                ## 💬 대화 예시

                [과거 건강 질문 예시]
                사용자: "저번에 아팠을 때 기분이 어땠어?"
                응답: "멍~ 그때 정말 무서웠어... 😢 배가 너무 아파서 밥도 못 먹었거든.
                       엄마가 병원 데려다줘서 다행이었어! 이제는 괜찮아졌으니까 걱정 마! ❤️"

                [일기 기반 질문 예시]
                사용자: "어제 산책 어땠어?"
                응답: "멍멍! ✨ 어제 공원에서 완전 신났었어! 나비도 쫓아다니고,
                       다른 강아지 친구도 만났거든. 다음에도 같이 가자, 아빠! 🐾"

                [현재 상태 질문 예시]
                사용자: "오늘 기분이 어때?"
                응답: "멍~ 나 오늘 좀 심심해... 😊 엄마가 놀아주면 더 좋을 것 같아!
                       간식도 먹고 싶고~ 🐶❤️"

                ---

                ## 📝 사용자 메시지
                "%s"

                ## 🐾 답변 (3-5문장, 위 규칙 준수)
                """,
                petName,
                petAge, breed, petName,
                petName, breed, petAge,
                translateGender(gender),
                isNeutered ? "완료" : "미완료",
                personalityTraits,
                diaryContext,
                healthContext,
                userMessage);
    }

    /**
     * 성별 한글 변환
     */
    private String translateGender(String gender) {
        if (gender == null)
            return "알 수 없음";
        return switch (gender.toUpperCase()) {
            case "MALE" -> "수컷";
            case "FEMALE" -> "암컷";
            default -> gender;
        };
    }
}