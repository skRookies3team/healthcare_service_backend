package com.petlog.healthcare.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 건강 기록 응답 DTO
 * WHY: 프론트엔드 HealthcarePage에 건강 데이터 제공
 *
 * @author healthcare-team
 * @since 2026-01-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthRecordResponse {

    private Long id;
    private Long petId;
    private String recordType;
    private LocalDate recordDate;
    private String content;
    private String severity;
    private String imageUrl;
    private LocalDateTime createdAt;

    /**
     * 주간 건강 요약 응답
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WeeklySummary {
        private Long petId;
        private String summary;
        private int totalRecords;
        private Double avgWeight;
        private Integer avgHeartRate;
        private Integer avgRespiratoryRate;
        private String healthStatus;
        private List<HealthRecordResponse> recentRecords;
    }

    /**
     * 바이탈 데이터 응답 (WithaPet 동기화용)
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VitalData {
        private Long petId;
        private Double weight;
        private Integer heartRate;
        private Integer respiratoryRate;
        private Integer steps;
        private Integer healthScore;
        private String status;
        private LocalDateTime syncedAt;
        private boolean vectorized; // Milvus 저장 여부
    }
}
