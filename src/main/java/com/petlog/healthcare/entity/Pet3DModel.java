package com.petlog.healthcare.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Pet 3D Model Entity
 * 펫별 3D 모델 정보 저장
 *
 * WHY?
 * - 펫별로 생성된 3D 모델을 영구 저장
 * - 한 번 생성하면 바꾸기 전까지 유지
 * - 여러 마리 펫별로 다른 3D 모델 관리
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Entity
@Table(name = "pet_3d_models", indexes = {
        @Index(name = "idx_pet_id", columnList = "petId"),
        @Index(name = "idx_user_id", columnList = "userId")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Pet3DModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 사용자 ID (UUID 형식)
     */
    @Column(nullable = false)
    private String userId;

    /**
     * 반려동물 ID
     */
    @Column(nullable = false)
    private Long petId;

    /**
     * 펫 이름 (조회 편의용)
     */
    @Column(length = 100)
    private String petName;

    /**
     * 원본 이미지 URL (S3)
     */
    @Column(length = 2048)
    private String sourceImageUrl;

    /**
     * 3D 모델 GLB 파일 URL
     */
    @Column(length = 2048)
    private String modelUrl;

    /**
     * 3D 모델 썸네일 이미지 URL
     */
    @Column(length = 2048)
    private String thumbnailUrl;

    /**
     * Meshy Task ID (재조회용)
     */
    @Column(length = 100)
    private String meshyTaskId;

    /**
     * 모델 상태
     * PENDING, PROCESSING, SUCCEEDED, FAILED
     */
    @Column(length = 20)
    private String status;

    /**
     * 생성 진행률 (0-100)
     */
    private Integer progress;

    /**
     * 텍스처 적용 여부 (Refine 완료)
     */
    @Column(nullable = false)
    private boolean textured = false;

    /**
     * 생성 일시
     */
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 수정 일시
     */
    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder
    public Pet3DModel(String userId, Long petId, String petName, String sourceImageUrl,
            String modelUrl, String thumbnailUrl, String meshyTaskId,
            String status, Integer progress, boolean textured) {
        this.userId = userId;
        this.petId = petId;
        this.petName = petName;
        this.sourceImageUrl = sourceImageUrl;
        this.modelUrl = modelUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.meshyTaskId = meshyTaskId;
        this.status = status;
        this.progress = progress;
        this.textured = textured;
    }

    /**
     * 상태 업데이트
     */
    public void updateStatus(String status, Integer progress) {
        this.status = status;
        this.progress = progress;
    }

    /**
     * 모델 URL 업데이트 (완료 시)
     */
    public void updateModelUrl(String modelUrl, String thumbnailUrl) {
        this.modelUrl = modelUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.status = "SUCCEEDED";
        this.progress = 100;
    }

    /**
     * Refine 완료 (텍스처 적용)
     */
    public void markAsTextured(String modelUrl, String thumbnailUrl, String refineTaskId) {
        this.modelUrl = modelUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.meshyTaskId = refineTaskId;
        this.textured = true;
        this.status = "SUCCEEDED";
        this.progress = 100;
    }
}
