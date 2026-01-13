package com.petlog.healthcare.domain.repository;

import com.petlog.healthcare.domain.entity.HealthRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * 건강 기록 Repository
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Repository
public interface HealthRecordRepository extends JpaRepository<HealthRecord, Long> {

        /**
         * 특정 펫의 최근 건강 기록 조회
         */
        List<HealthRecord> findByPetIdOrderByRecordDateDesc(Long petId);

        /**
         * 특정 펫의 특정 유형 건강 기록 조회
         */
        List<HealthRecord> findByPetIdAndRecordTypeOrderByRecordDateDesc(Long petId, String recordType);

        /**
         * 특정 기간 내 건강 기록 조회
         */
        @Query("SELECT h FROM HealthRecord h WHERE h.petId = :petId " +
                        "AND h.recordDate BETWEEN :startDate AND :endDate " +
                        "ORDER BY h.recordDate DESC")
        List<HealthRecord> findByPetIdAndDateRange(
                        @Param("petId") Long petId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        /**
         * 특정 사용자의 모든 펫 건강 기록
         */
        List<HealthRecord> findByUserIdOrderByRecordDateDesc(String userId);

        /**
         * 심각도별 기록 조회
         */
        List<HealthRecord> findByPetIdAndSeverityOrderByRecordDateDesc(Long petId, String severity);
}
