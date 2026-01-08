package com.petlog.healthcare.domain.repository;

import com.petlog.healthcare.domain.entity.DiaryMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Diary Memory Repository
 * PostgreSQL에서 일기 메모리 메타데이터 조회
 *
 * WHY? 실제 벡터 검색은 Milvus에서, 메타데이터는 PostgreSQL에서 관리
 * ACID 트랜잭션과 벡터 검색 성능 동시 확보
 */
@Repository
public interface DiaryMemoryRepository extends JpaRepository<DiaryMemory, Long> {

    /**
     * 사용자-반려동물의 모든 일기 메모리 조회
     */
    List<DiaryMemory> findByUserIdAndPetId(Long userId, Long petId);

    /**
     * 특정 기간의 일기 메모리 조회
     */
    @Query("SELECT dm FROM DiaryMemory dm " +
            "WHERE dm.userId = :userId AND dm.petId = :petId " +
            "AND dm.createdAt >= :startDate AND dm.createdAt <= :endDate " +
            "ORDER BY dm.createdAt DESC")
    List<DiaryMemory> findRecentDiaries(
            @Param("userId") Long userId,
            @Param("petId") Long petId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    /**
     * 일기 ID로 조회
     */
    DiaryMemory findByDiaryId(Long diaryId);
}
