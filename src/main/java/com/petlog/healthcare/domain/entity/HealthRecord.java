package com.petlog.healthcare.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 반려동물 건강 기록 Entity
 *
 * 피부질환 분석, 병원 방문, 건강 상태 등 기록
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Entity
@Table(name = "health_records")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HealthRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자 ID (User Service 참조 - UUID 형식)
     */
    @Column(name = "user_id", nullable = false)
    private String userId;

    /**
     * 반려동물 ID (User Service 참조)
     */
    @Column(name = "pet_id", nullable = false)
    private Long petId;

    /**
     * 기록 유형
     * SKIN_ANALYSIS: 피부질환 분석
     * HOSPITAL_VISIT: 병원 방문
     * WEIGHT: 체중 기록
     * VACCINATION: 예방접종
     * SYMPTOM: 증상 기록
     */
    @Column(name = "record_type", nullable = false, length = 50)
    private String recordType;

    /**
     * 기록 날짜
     */
    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    /**
     * 기록 내용 (JSON 또는 텍스트)
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 심각도 (NORMAL, MILD, MODERATE, SEVERE)
     */
    @Column(name = "severity", length = 20)
    private String severity;

    /**
     * 이미지 URL (S3)
     */
    @Column(name = "image_url", length = 500)
    private String imageUrl;

    /**
     * 추가 메모
     */
    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
