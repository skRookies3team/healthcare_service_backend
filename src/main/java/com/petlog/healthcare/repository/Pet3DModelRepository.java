package com.petlog.healthcare.repository;

import com.petlog.healthcare.entity.Pet3DModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Pet 3D Model Repository
 * 펫별 3D 모델 저장소
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Repository
public interface Pet3DModelRepository extends JpaRepository<Pet3DModel, Long> {

    /**
     * 특정 펫의 최신 3D 모델 조회
     * WHY: 펫별로 가장 최근 생성된 모델을 조회
     */
    Optional<Pet3DModel> findTopByPetIdOrderByCreatedAtDesc(Long petId);

    /**
     * 특정 펫의 완료된 3D 모델 조회
     * WHY: SUCCEEDED 상태의 모델만 조회
     */
    Optional<Pet3DModel> findTopByPetIdAndStatusOrderByCreatedAtDesc(Long petId, String status);

    /**
     * 사용자의 모든 펫 3D 모델 조회
     * WHY: 사용자별 모든 펫의 3D 모델 목록 조회
     */
    List<Pet3DModel> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * 특정 펫의 모든 3D 모델 조회
     * WHY: 펫별 3D 모델 히스토리
     */
    List<Pet3DModel> findByPetIdOrderByCreatedAtDesc(Long petId);

    /**
     * Meshy Task ID로 조회
     * WHY: 상태 업데이트 시 사용
     */
    Optional<Pet3DModel> findByMeshyTaskId(String meshyTaskId);

    /**
     * 특정 펫에 3D 모델이 존재하는지 확인
     */
    boolean existsByPetIdAndStatus(Long petId, String status);
}
