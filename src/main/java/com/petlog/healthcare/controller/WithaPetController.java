package com.petlog.healthcare.controller;

import com.petlog.healthcare.dto.withapet.WithaPetHealthData;
import com.petlog.healthcare.infrastructure.milvus.MilvusVectorStore;
import com.petlog.healthcare.service.WithaPetMockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WithaPet 스마트 청진기 연동 Controller
 * WHY: WITHAPET 기기 연동 전 목업 데이터 API 제공
 * 프론트엔드 건강 모니터링 화면 개발 지원
 * 
 * 추가 기능:
 * - POST /api/withapet/sync: 데이터 동기화 후 Milvus 벡터 저장
 */
@Slf4j
@RestController
@RequestMapping("/api/withapet")
@RequiredArgsConstructor
@Tag(name = "WithaPet", description = "스마트 청진기 연동 API")
public class WithaPetController {

    private final WithaPetMockService withaPetMockService;
    private final MilvusVectorStore milvusVectorStore;

    /**
     * 목업 건강 데이터 조회
     * - 사용자 펫 이름으로 데이터 치환
     * - 건강 현황 모니터링 화면용
     */
    @GetMapping("/health/mock")
    @Operation(summary = "목업 건강 데이터 조회", description = "WithaPet 연동 전 목업 데이터 반환. 펫 이름으로 치환됨")
    public ResponseEntity<Map<String, Object>> getMockHealthData(
            @Parameter(description = "펫 이름", required = true) @RequestParam String petName,

            @Parameter(description = "펫 종류 (Dog/Cat)") @RequestParam(required = false) String petType) {

        log.info("[WithaPet] Mock health data request for pet: {}, type: {}", petName, petType);

        WithaPetHealthData healthData = withaPetMockService.getMockHealthData(petName, petType);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "건강 데이터 조회 성공 (목업)");
        response.put("data", healthData);

        return ResponseEntity.ok(response);
    }

    /**
     * 건강 데이터 저장 (수동 입력)
     * - 프론트엔드에서 직접 입력한 데이터 저장
     */
    @PostMapping("/health/record")
    @Operation(summary = "건강 데이터 수동 기록", description = "사용자가 직접 입력한 건강 데이터 저장")
    public ResponseEntity<Map<String, Object>> recordHealthData(
            @RequestBody HealthRecordRequest request) {

        log.info("[WithaPet] Manual health record: pet={}, weight={}, heartRate={}",
                request.getPetName(), request.getWeight(), request.getHeartRate());

        // TODO: 실제 DB 저장 구현 (현재는 Mock 응답)
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "건강 데이터가 기록되었습니다");
        response.put("data", Map.of(
                "petName", request.getPetName(),
                "weight", request.getWeight(),
                "heartRate", request.getHeartRate(),
                "respiratoryRate", request.getRespiratoryRate(),
                "steps", request.getSteps(),
                "condition", request.getCondition(),
                "notes", request.getNotes(),
                "recordedAt", java.time.LocalDateTime.now()));

        return ResponseEntity.ok(response);
    }

    /**
     * 샘플 데이터 목록 조회
     * - 33개 PDF 기반 샘플 데이터 확인용
     */
    @GetMapping("/samples")
    @Operation(summary = "샘플 데이터 목록", description = "목업용 샘플 데이터 목록 조회")
    public ResponseEntity<Map<String, Object>> getSampleList() {
        log.info("[WithaPet] Sample list request");

        List<Map<String, Object>> samples = withaPetMockService.getSampleList();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("count", samples.size());
        response.put("data", samples);

        return ResponseEntity.ok(response);
    }

    /**
     * 바이탈 트렌드 조회 (24시간)
     * - 심박수/호흡수 트렌드 차트용
     */
    @GetMapping("/trends/{petName}")
    @Operation(summary = "바이탈 트렌드 조회", description = "24시간 심박수/호흡수 트렌드 데이터")
    public ResponseEntity<Map<String, Object>> getVitalTrends(
            @PathVariable String petName,
            @RequestParam(defaultValue = "heartRate") String type) {

        log.info("[WithaPet] Vital trends request for pet: {}, type: {}", petName, type);

        WithaPetHealthData healthData = withaPetMockService.getMockHealthData(petName, null);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("petName", petName);
        response.put("type", type);

        if ("heartRate".equals(type)) {
            response.put("trends", healthData.getHeartRateTrend());
            response.put("average", healthData.getVitalData().getAvgHeartRate());
            response.put("unit", "BPM");
        } else {
            response.put("trends", healthData.getRespiratoryRateTrend());
            response.put("average", healthData.getVitalData().getAvgRespiratoryRate());
            response.put("unit", "RPM");
        }

        return ResponseEntity.ok(response);
    }

    /**
     * ✅ WithaPet 데이터 동기화 + Milvus 벡터 저장
     * WHY: 스마트 청진기 데이터를 Milvus에 저장하여 Persona Chatbot RAG에 활용
     */
    @PostMapping("/sync")
    @Operation(summary = "WithaPet 동기화", description = "건강 데이터를 불러와 AI 참고 자료로 저장")
    public ResponseEntity<Map<String, Object>> syncAndVectorize(
            @RequestParam String petName,
            @RequestParam(required = false) String petType,
            @RequestHeader(value = "X-USER-ID", required = false) String userId,
            @RequestHeader(value = "X-PET-ID", required = false, defaultValue = "0") Long petId) {

        log.info("═══════════════════════════════════════");
        log.info("🔄 WithaPet 동기화 + Milvus 저장");
        log.info("   Pet: {}, User-ID: {}, Pet-ID: {}", petName, userId, petId);
        log.info("═══════════════════════════════════════");

        try {
            // 1. 목업 건강 데이터 조회
            WithaPetHealthData healthData = withaPetMockService.getMockHealthData(petName, petType);

            // 2. 건강 요약 텍스트 생성
            String healthSummary = String.format(
                    "%s의 건강 상태: 심박수 %d BPM, 호흡수 %d RPM, 체중 %.1fkg, 건강점수 %d점 (%s). AI 분석 결과: %s",
                    petName,
                    healthData.getVitalData().getAvgHeartRate(),
                    healthData.getVitalData().getAvgRespiratoryRate(),
                    healthData.getVitalData().getWeight(),
                    healthData.getHealthScore(),
                    healthData.getHealthScore() >= 80 ? "양호" : "주의",
                    healthData.getAiAnalysis().getAnalysisResult());

            // 3. Milvus에 벡터 저장 (Persona Chatbot RAG용)
            boolean vectorized = milvusVectorStore.syncWithaPetData(userId, petId, healthSummary);

            log.info("✅ WithaPet 동기화 완료 - Vectorized: {}", vectorized);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "건강 데이터가 동기화되었습니다" + (vectorized ? " (AI 참고 자료로 등록됨)" : ""));
            response.put("vectorized", vectorized);
            response.put("data", healthData);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("❌ WithaPet 동기화 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "동기화 실패: " + e.getMessage()));
        }
    }

    /**
     * 건강 데이터 기록 요청 DTO
     */
    @lombok.Data
    public static class HealthRecordRequest {
        private String petName;
        private Double weight; // 체중 (kg)
        private Integer heartRate; // 심박수 (BPM)
        private Integer respiratoryRate; // 호흡수 (회/분)
        private Integer steps; // 걸음수
        private String condition; // 컨디션 (Good, Bad, etc.)
        private String notes; // 메모
    }
}
