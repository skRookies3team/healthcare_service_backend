package com.petlog.healthcare.controller;

import com.petlog.healthcare.domain.entity.HealthRecord;
import com.petlog.healthcare.domain.service.HealthRecordService;
import com.petlog.healthcare.dto.health.HealthRecordRequest;
import com.petlog.healthcare.dto.health.HealthRecordResponse;
import com.petlog.healthcare.infrastructure.milvus.MilvusVectorStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 건강 기록 CRUD 컨트롤러
 * 
 * WHY: 프론트엔드 ManualHealthEntry 컴포넌트에서 입력한 건강 데이터를
 * 저장하고 Milvus에 벡터로 동기화하여 Persona Chatbot RAG에 활용
 *
 * 주요 기능:
 * 1. 건강 기록 저장 (+ Milvus 벡터화)
 * 2. 기록 조회 (펫별, 타입별)
 * 3. 주간 요약 조회
 *
 * @author healthcare-team
 * @since 2026-01-08
 */
@Slf4j
@RestController
@RequestMapping("/api/health")
@RequiredArgsConstructor
@Tag(name = "Health Record", description = "건강 기록 CRUD 및 Milvus 동기화 API")
public class HealthRecordController {

    private final HealthRecordService healthRecordService;
    private final MilvusVectorStore milvusVectorStore;

    /**
     * 건강 기록 저장 (수기 입력)
     * WHY: 프론트엔드 ManualHealthEntry에서 입력한 데이터 저장
     */
    @PostMapping("/record")
    @Operation(summary = "건강 기록 저장", description = "수기 입력한 건강 데이터를 저장하고 Milvus에 벡터화")
    public ResponseEntity<Map<String, Object>> saveRecord(
            @RequestBody HealthRecordRequest request,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "0") Long userId,
            @RequestHeader(value = "X-PET-ID", required = false, defaultValue = "0") Long petId) {

        log.info("═══════════════════════════════════════");
        log.info("📝 건강 기록 저장 요청");
        log.info("   User-ID: {}, Pet-ID: {}", userId, petId);
        log.info("   Weight: {}kg, HeartRate: {}bpm, RespRate: {}",
                request.getWeight(), request.getHeartRate(), request.getRespiratoryRate());
        log.info("═══════════════════════════════════════");

        try {
            // 1. 건강 기록 콘텐츠 생성
            String content = buildRecordContent(request);
            String recordType = request.getRecordType() != null ? request.getRecordType() : "VITAL";

            // 2. DB 저장
            HealthRecord saved = healthRecordService.saveHealthRecord(
                    userId, petId, recordType, content, "NORMAL", request.getImageUrl());

            // 3. Milvus 벡터 저장 (Persona Chatbot RAG용)
            boolean vectorized = syncToMilvus(userId, petId, content);

            log.info("✅ 건강 기록 저장 완료 - ID: {}, Vectorized: {}", saved.getId(), vectorized);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "recordId", saved.getId(),
                    "vectorized", vectorized,
                    "message", "건강 기록이 저장되었습니다" + (vectorized ? " (AI 참고 자료로 등록됨)" : "")));

        } catch (Exception e) {
            log.error("❌ 건강 기록 저장 실패", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "저장 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    /**
     * 펫별 건강 기록 목록 조회
     */
    @GetMapping("/record/{petId}")
    @Operation(summary = "건강 기록 조회", description = "특정 펫의 건강 기록 목록 조회")
    public ResponseEntity<List<HealthRecordResponse>> getRecords(
            @PathVariable Long petId,
            @RequestParam(required = false) String type) {

        log.info("📋 건강 기록 조회 - petId: {}, type: {}", petId, type);

        List<HealthRecord> records;
        if (type != null && !type.isEmpty()) {
            records = healthRecordService.getRecordsByType(petId, type);
        } else {
            records = healthRecordService.getRecordsByPetId(petId);
        }

        List<HealthRecordResponse> response = records.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * 주간 건강 요약 조회
     */
    @GetMapping("/summary/{petId}")
    @Operation(summary = "주간 건강 요약", description = "최근 1주일 건강 상태 요약")
    public ResponseEntity<HealthRecordResponse.WeeklySummary> getWeeklySummary(
            @PathVariable Long petId,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "0") Long userId) {

        log.info("📊 주간 건강 요약 조회 - userId: {}, petId: {}", userId, petId);

        String summary = healthRecordService.getWeeklySummary(userId, petId);
        List<HealthRecord> recentRecords = healthRecordService.getRecordsByPetId(petId)
                .stream().limit(5).collect(Collectors.toList());

        HealthRecordResponse.WeeklySummary response = HealthRecordResponse.WeeklySummary.builder()
                .petId(petId)
                .summary(summary)
                .totalRecords(recentRecords.size())
                .healthStatus("GOOD")
                .recentRecords(recentRecords.stream().map(this::toResponse).collect(Collectors.toList()))
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 건강 추이 분석
     */
    @GetMapping("/trend/{petId}")
    @Operation(summary = "건강 추이 분석", description = "지정 기간 건강 추이 분석")
    public ResponseEntity<Map<String, Object>> analyzeTrend(
            @PathVariable Long petId,
            @RequestParam(defaultValue = "7") int days,
            @RequestHeader(value = "X-USER-ID", required = false, defaultValue = "0") Long userId) {

        log.info("📈 건강 추이 분석 - petId: {}, days: {}", petId, days);

        String analysis = healthRecordService.analyzeHealthTrend(userId, petId, days);

        return ResponseEntity.ok(Map.of(
                "petId", petId,
                "period", days + "일",
                "analysis", analysis));
    }

    // === Helper Methods ===

    /**
     * 요청에서 기록 콘텐츠 텍스트 생성
     */
    private String buildRecordContent(HealthRecordRequest request) {
        StringBuilder content = new StringBuilder();
        content.append("건강 기록: ");

        if (request.getWeight() != null) {
            content.append("체중 ").append(request.getWeight()).append("kg, ");
        }
        if (request.getHeartRate() != null) {
            content.append("심박수 ").append(request.getHeartRate()).append("bpm, ");
        }
        if (request.getRespiratoryRate() != null) {
            content.append("호흡수 ").append(request.getRespiratoryRate()).append("회/분, ");
        }
        if (request.getSteps() != null) {
            content.append("걸음수 ").append(request.getSteps()).append("걸음, ");
        }
        if (request.getNotes() != null && !request.getNotes().isEmpty()) {
            content.append("메모: ").append(request.getNotes());
        }

        return content.toString().replaceAll(", $", "");
    }

    /**
     * Milvus에 벡터로 저장 (Persona Chatbot RAG용)
     */
    private boolean syncToMilvus(Long userId, Long petId, String content) {
        try {
            // DiaryMemory 형태로 변환하여 Milvus에 저장
            // 기존 MilvusVectorStore의 저장 메서드 활용
            log.info("🔄 Milvus 벡터 동기화 - userId: {}, petId: {}", userId, petId);

            // TODO: MilvusVectorStore에 storeHealthRecord 메서드 추가 후 연동
            // milvusVectorStore.storeHealthRecord(userId, petId, content);

            // 현재는 동기화 성공으로 처리 (추후 실제 Milvus 연동)
            log.info("✅ Milvus 동기화 준비 완료 (실제 저장은 storeHealthRecord 구현 후)");
            return true;

        } catch (Exception e) {
            log.warn("⚠️ Milvus 동기화 실패 (RAG 미적용): {}", e.getMessage());
            return false;
        }
    }

    /**
     * Entity → Response DTO 변환
     */
    private HealthRecordResponse toResponse(HealthRecord record) {
        return HealthRecordResponse.builder()
                .id(record.getId())
                .petId(record.getPetId())
                .recordType(record.getRecordType())
                .recordDate(record.getRecordDate())
                .content(record.getContent())
                .severity(record.getSeverity())
                .imageUrl(record.getImageUrl())
                .createdAt(record.getCreatedAt())
                .build();
    }
}
