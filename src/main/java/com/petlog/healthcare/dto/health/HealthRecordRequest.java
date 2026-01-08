package com.petlog.healthcare.dto.health;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 건강 기록 저장 요청 DTO
 * WHY: 프론트엔드 ManualHealthEntry 컴포넌트에서 전송하는 데이터 매핑
 *
 * @author healthcare-team
 * @since 2026-01-08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthRecordRequest {

    /**
     * 체중 (kg)
     */
    private Double weight;

    /**
     * 심박수 (BPM)
     */
    private Integer heartRate;

    /**
     * 호흡수 (회/분)
     */
    private Integer respiratoryRate;

    /**
     * 걸음수
     */
    private Integer steps;

    /**
     * 기록 타입 (VITAL, ACTIVITY, SYMPTOM 등)
     */
    private String recordType;

    /**
     * 추가 메모/설명
     */
    private String notes;

    /**
     * 이미지 URL (피부질환 등)
     */
    private String imageUrl;
}
