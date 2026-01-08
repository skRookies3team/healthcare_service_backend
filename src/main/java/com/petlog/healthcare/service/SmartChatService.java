package com.petlog.healthcare.service;

import com.petlog.healthcare.dto.hospital.HospitalResponse;
import com.petlog.healthcare.dto.hospital.HospitalResponse.HospitalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.petlog.healthcare.domain.service.HealthRecordService;

/**
 * 스마트 챗봇 서비스
 *
 * 사용자 질문을 분석하여 적절한 기능으로 라우팅
 * - 피부 관련 질문 → 피부질환 탐지 안내
 * - 병원 관련 질문 → 동물병원 검색 결과 포함
 * - 일반 질문 → ⭐ RAG 기반 수의사 지식 검색 후 응답
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmartChatService {

    private final ClaudeService claudeService;
    private final HospitalService hospitalService;
    private final VetKnowledgeSearchService vetKnowledgeSearchService; // ⭐ RAG 서비스
    private final HealthRecordService healthRecordService; // ⭐ 건강 기록 서비스

    // 피부 관련 키워드
    private static final Pattern SKIN_PATTERN = Pattern.compile(
            "(피부|피부병|피부질환|습진|탈모|털빠짐|가려움|긁|붉|발진|" +
                    "미란|결절|궤양|비듬|딱지|상처|염증|알러지|알레르기|" +
                    "두드러기|무좀|진드기|벼룩|기생충|곰팡이|핫스팟)",
            Pattern.CASE_INSENSITIVE);

    // 병원 관련 키워드
    private static final Pattern HOSPITAL_PATTERN = Pattern.compile(
            "(병원|동물병원|수의사|진료|응급|24시|야간|가까운|근처|주변|" +
                    "어디|찾|검색|추천|소개|연락처|전화|위치)",
            Pattern.CASE_INSENSITIVE);

    // 지역명 패턴
    private static final Pattern REGION_PATTERN = Pattern.compile(
            "(서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|" +
                    "전북|전남|경북|경남|제주|강남|강북|송파|마포|용산|종로|" +
                    "서초|영등포|성동|광진|동대문|중랑|성북|강서|양천|구로|" +
                    "금천|관악|동작|노원|도봉|은평|[가-힣]+구|[가-힣]+시)",
            Pattern.CASE_INSENSITIVE);

    // ⭐ 진료과 감지 패턴
    private static final Pattern INTERNAL_PATTERN = Pattern.compile(
            "(구토|설사|변비|식욕|소화|위장|간|신장|당뇨|췌장|심장|호흡|기침|" +
                    "재채기|콧물|열|발열|무기력|식이|먹|토|배|복통|장염|요로)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern EYE_PATTERN = Pattern.compile(
            "(눈|눈물|충혈|눈곱|각막|백내장|녹내장|안구|시력|눈부심|결막)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern DENTAL_PATTERN = Pattern.compile(
            "(이빨|이|치아|잇몸|입냄새|구취|치석|구강|입|치주|발치)",
            Pattern.CASE_INSENSITIVE);

    /**
     * 스마트 챗봇 - 의도 감지 후 적절한 응답 생성
     *
     * @param message 사용자 메시지
     * @return 스마트 응답 (기능 연동 포함)
     */
    /**
     * 스마트 챗봇 - 의도 감지 후 적절한 응답 생성
     *
     * @param message 사용자 메시지
     * @param userId  사용자 ID
     * @param petId   반려동물 ID
     * @return 스마트 응답 (기능 연동 포함)
     */
    public Map<String, Object> smartChat(String message, Long userId, Long petId) {
        log.info("🧠 [스마트 챗봇] 의도 분석: {}", truncate(message, 50));

        // 1. 피부 관련 질문 감지
        if (isSkinRelated(message)) {
            log.info("🔬 피부 관련 질문 감지");
            return handleSkinQuery(message);
        }

        // 2. 병원 관련 질문 감지
        if (isHospitalRelated(message)) {
            log.info("🏥 병원 관련 질문 감지");
            return handleHospitalQuery(message);
        }

        // 3. 일반 질문 - 기존 수의사 모드
        log.info("💬 일반 건강 상담");
        return handleGeneralQuery(message, userId, petId);
    }

    /**
     * 피부 관련 질문 처리
     */
    private Map<String, Object> handleSkinQuery(String message) {
        // 일반 수의사 응답 + 피부질환 탐지 안내
        String baseResponse = claudeService.chat(message);

        String enhancedResponse = baseResponse + "\n\n" +
                "---\n" +
                "💡 **피부질환 AI 분석 기능**\n" +
                "반려동물의 피부 사진을 업로드하시면 AI가 분석해드립니다.\n" +
                "📸 `POST /api/skin-disease/analyze` 에서 이미지를 업로드하세요.\n" +
                "\n" +
                "⚠️ AI 분석은 참고용이며, 정확한 진단은 수의사와 상담하세요.";

        return Map.of(
                "success", true,
                "intent", "SKIN_DISEASE",
                "response", enhancedResponse,
                "ragUsed", false,
                "department", "피부과",
                "features", Map.of(
                        "skinDiseaseAnalysis", true,
                        "endpoint", "/api/skin-disease/analyze",
                        "method", "POST",
                        "description", "피부 사진 업로드하여 AI 분석 받기"));
    }

    /**
     * 병원 관련 질문 처리
     */
    private Map<String, Object> handleHospitalQuery(String message) {
        // 지역 추출
        String region = extractRegion(message);

        // 병원 검색
        HospitalResponse hospitals;
        if (region != null) {
            log.info("   🗺️ 지역 감지: {}", region);
            hospitals = hospitalService.findByRegion(region);
        } else {
            // 지역 미지정 시 응급 병원 또는 기본 검색
            if (message.contains("응급") || message.contains("24시")) {
                hospitals = hospitalService.findEmergencyHospitals();
            } else {
                hospitals = hospitalService.findNearbyHospitals(37.5, 127.0, 10);
            }
        }

        // 병원 정보 텍스트 생성
        String hospitalInfo = formatHospitalList(hospitals.getHospitals());

        // 응답 생성
        String response = buildHospitalResponse(message, region, hospitalInfo);

        return Map.of(
                "success", true,
                "intent", "HOSPITAL_SEARCH",
                "response", response,
                "hospitals", hospitals.getHospitals().stream().limit(5).toList(),
                "totalCount", hospitals.getTotalCount(),
                "features", Map.of(
                        "hospitalSearch", true,
                        "nearbyEndpoint", "/api/hospital/nearby",
                        "searchEndpoint", "/api/hospital/search"),
                "ragUsed", false,
                "department", "병원 검색");
    }

    /**
     * ⭐ 일반 건강 질문 처리 (RAG 기반 + 건강 기록 연동)
     *
     * 관련 수의사 지식 베이스를 검색하여 컨텍스트로 활용
     */
    private Map<String, Object> handleGeneralQuery(String message, Long userId, Long petId) {
        // 1. 진료과 감지
        String department = detectDepartment(message);
        log.info("   📋 진료과 감지: {}", department != null ? department : "전체");

        // 2. RAG 컨텍스트 검색 (수의학 지식)
        String ragContext = vetKnowledgeSearchService.buildRAGContext(message, department, 3);

        // 3. 건강 기록 컨텍스트 조회 (반려동물 건강 데이터)
        String healthContext = "";
        if (userId > 0 && petId > 0) {
            try {
                healthContext = healthRecordService.getWeeklySummary(userId, petId);
                if (!healthContext.isEmpty()) {
                    log.info("   🏥 건강 기록 컨텍스트 추가 완료");
                }
            } catch (Exception e) {
                log.warn("   ⚠️ 건강 기록 조회 실패 (무시됨)", e);
            }
        }

        String response;
        boolean hasKnowledge = !ragContext.isEmpty();
        boolean hasHealth = !healthContext.isEmpty();
        // RAG나 건강 기록 중 하나라도 있으면 전문 지식/데이터 사용으로 간주
        boolean ragUsed = hasKnowledge || hasHealth;

        // 4. 프롬프트 구성
        if (ragUsed) {
            // RAG 또는 건강 기록이 있으면 강화된 프롬프트 사용
            String enhancedPrompt = buildEnhancedPrompt(message, ragContext, healthContext);
            response = claudeService.chat(enhancedPrompt);
            log.info("   📚 지식/데이터 기반 응답 생성 (지식: {}, 건강기록: {})",
                    hasKnowledge ? "O" : "X", hasHealth ? "O" : "X");
        } else {
            // 정보가 없으면 기본 방식
            response = claudeService.chat(message);
            log.info("   💬 기본 수의사 모드");
        }

        return Map.of(
                "success", true,
                "intent", "GENERAL_HEALTH",
                "response", response,
                "ragUsed", ragUsed,
                "department", department != null ? department : "전체");
    }

    /**
     * 강화된 프롬프트 생성 (수의학 지식 + 건강 기록)
     */
    private String buildEnhancedPrompt(String userQuestion, String vetKnowledge, String healthRecord) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("당신은 전문 수의사 AI '닥터 펫'입니다.\n");
        prompt.append("아래 제공된 [수의학 지식]과 [반려동물 건강 기록]을 바탕으로 보호자의 질문에 친절하고 전문적으로 답변해주세요.\n\n");

        if (vetKnowledge != null && !vetKnowledge.isEmpty()) {
            prompt.append("=== 📚 참고할 수의학 지식 ===\n");
            prompt.append(vetKnowledge).append("\n\n");
        }

        if (healthRecord != null && !healthRecord.isEmpty()) {
            prompt.append("=== 🏥 반려동물 최근 건강 기록 ===\n");
            prompt.append(healthRecord).append("\n\n");
        }

        prompt.append("=== 보호자 질문 ===\n");
        prompt.append(userQuestion).append("\n\n");

        prompt.append("답변 가이드:\n");
        prompt.append("1. 위 정보를 종합하여 구체적인 조언을 제공하세요.\n");
        prompt.append("2. 건강 기록이 있다면 그 수치나 변화를 언급하며 조언하세요.\n");
        prompt.append("3. 심각해 보이는 증상은 반드시 병원 방문을 권유하세요.\n");
        prompt.append("4. 너무 길지 않게 핵심을 전달하세요.\n");

        return prompt.toString();
    }

    /**
     * (Deprecated) 기존 단순 RAG 프롬프트 빌더 - 하위 호환성 유지용
     */
    public String buildRAGPrompt(String userQuestion, String ragContext) {
        return buildEnhancedPrompt(userQuestion, ragContext, "");
    }

    /**
     * 진료과 감지
     */
    private String detectDepartment(String message) {
        if (SKIN_PATTERN.matcher(message).find())
            return "피부과";
        if (INTERNAL_PATTERN.matcher(message).find())
            return "내과";
        if (EYE_PATTERN.matcher(message).find())
            return "안과";
        if (DENTAL_PATTERN.matcher(message).find())
            return "치과";
        return null; // 전체 검색
    }

    /**
     * 피부 관련 질문 감지
     */
    private boolean isSkinRelated(String message) {
        return SKIN_PATTERN.matcher(message).find();
    }

    /**
     * 병원 관련 질문 감지
     */
    private boolean isHospitalRelated(String message) {
        return HOSPITAL_PATTERN.matcher(message).find();
    }

    /**
     * 지역명 추출
     */
    private String extractRegion(String message) {
        var matcher = REGION_PATTERN.matcher(message);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    /**
     * 병원 목록 텍스트 포맷
     */
    private String formatHospitalList(List<HospitalInfo> hospitals) {
        if (hospitals.isEmpty()) {
            return "검색된 병원이 없습니다.";
        }

        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (HospitalInfo h : hospitals) {
            if (count >= 3)
                break; // 최대 3개만 표시
            sb.append(String.format("\n🏥 **%s**%s\n",
                    h.getName(),
                    h.isEmergency() ? " (24시/응급)" : ""));
            sb.append(String.format("   📍 %s\n", h.getRoadAddress()));
            sb.append(String.format("   📞 %s\n", h.getPhone()));
            count++;
        }

        if (hospitals.size() > 3) {
            sb.append(String.format("\n...외 %d개 병원\n", hospitals.size() - 3));
        }

        return sb.toString();
    }

    /**
     * 병원 응답 텍스트 생성
     */
    private String buildHospitalResponse(String message, String region, String hospitalInfo) {
        StringBuilder response = new StringBuilder();

        if (region != null) {
            response.append(String.format("🏥 **%s 지역 동물병원** 검색 결과입니다.\n", region));
        } else {
            response.append("🏥 **주변 동물병원** 검색 결과입니다.\n");
        }

        response.append(hospitalInfo);
        response.append("\n---\n");
        response.append("📍 더 많은 병원 정보: `GET /api/hospital/search?region=지역명`\n");
        response.append("📍 위치 기반 검색: `GET /api/hospital/nearby?lat=위도&lng=경도`");

        return response.toString();
    }

    /**
     * 텍스트 자르기
     */
    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen)
            return text;
        return text.substring(0, maxLen) + "...";
    }
}
