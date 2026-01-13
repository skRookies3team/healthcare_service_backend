package com.petlog.healthcare.domain.service;

import com.petlog.healthcare.domain.entity.HealthRecord;
import com.petlog.healthcare.domain.repository.HealthRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Health Record Service
 * 반려동물 건강 기록 관리 및 요약
 *
 * @author healthcare-team
 * @since 2026-01-07 (DB 연동)
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HealthRecordService {

    private final HealthRecordRepository healthRecordRepository;

    /**
     * 최근 1주일 건강 요약 조회
     *
     * @param userId 사용자 ID
     * @param petId  반려동물 ID
     * @return 건강 요약 텍스트
     */
    public String getWeeklySummary(String userId, Long petId) {
        log.info("🏥 건강 기록 요약 조회 - userId: {}, petId: {}", userId, petId);

        try {
            LocalDate today = LocalDate.now();
            LocalDate weekAgo = today.minusDays(7);

            List<HealthRecord> records = healthRecordRepository
                    .findByPetIdAndDateRange(petId, weekAgo, today);

            if (records.isEmpty()) {
                return "최근 1주일 건강 기록이 없습니다.";
            }

            return buildSummary(records);

        } catch (Exception e) {
            log.error("❌ 건강 기록 조회 실패 - userId: {}, petId: {}", userId, petId, e);
            return "(건강 기록을 불러올 수 없습니다)";
        }
    }

    /**
     * 건강 기록 요약 텍스트 생성
     */
    private String buildSummary(List<HealthRecord> records) {
        StringBuilder summary = new StringBuilder();
        summary.append("최근 1주일 건강 상태 요약:\n\n");

        for (HealthRecord record : records) {
            summary.append(String.format("📅 %s [%s]\n",
                    record.getRecordDate().format(DateTimeFormatter.ofPattern("MM/dd")),
                    record.getRecordType()));
            if (record.getSeverity() != null) {
                summary.append(String.format("   심각도: %s\n", record.getSeverity()));
            }
            if (record.getContent() != null) {
                summary.append(String.format("   내용: %s\n", truncate(record.getContent(), 100)));
            }
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * 건강 기록 저장
     */
    @Transactional
    public HealthRecord saveHealthRecord(String userId, Long petId, String recordType,
            String content, String severity, String imageUrl) {
        log.info("💾 건강 기록 저장 - userId: {}, petId: {}, type: {}",
                userId, petId, recordType);

        HealthRecord record = HealthRecord.builder()
                .userId(userId)
                .petId(petId)
                .recordType(recordType)
                .recordDate(LocalDate.now())
                .content(content)
                .severity(severity)
                .imageUrl(imageUrl)
                .build();

        return healthRecordRepository.save(record);
    }

    /**
     * 피부질환 분석 결과 저장
     */
    @Transactional
    public void saveSkinAnalysisRecord(String userId, Long petId, String analysisResult,
            String severity, String imageUrl) {
        log.info("🔬 피부질환 분석 기록 저장 - userId: {}, petId: {}", userId, petId);

        saveHealthRecord(userId, petId, "SKIN_ANALYSIS", analysisResult, severity, imageUrl);
    }

    /**
     * 특정 펫의 모든 기록 조회
     */
    public List<HealthRecord> getRecordsByPetId(Long petId) {
        return healthRecordRepository.findByPetIdOrderByRecordDateDesc(petId);
    }

    /**
     * 특정 유형의 기록 조회
     */
    public List<HealthRecord> getRecordsByType(Long petId, String recordType) {
        return healthRecordRepository.findByPetIdAndRecordTypeOrderByRecordDateDesc(petId, recordType);
    }

    /**
     * 특정 기간 건강 추이 분석
     */
    public String analyzeHealthTrend(String userId, Long petId, int days) {
        log.info("📊 건강 추이 분석 - userId: {}, petId: {}, days: {}",
                userId, petId, days);

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days);

        List<HealthRecord> records = healthRecordRepository
                .findByPetIdAndDateRange(petId, startDate, today);

        if (records.isEmpty()) {
            return String.format("최근 %d일간 기록이 없습니다.", days);
        }

        // 간단한 분석
        long severeCount = records.stream()
                .filter(r -> "SEVERE".equals(r.getSeverity()))
                .count();

        return String.format("최근 %d일간 총 %d건의 건강 기록이 있습니다. (심각도 높음: %d건)",
                days, records.size(), severeCount);
    }

    private String truncate(String text, int maxLen) {
        if (text == null || text.length() <= maxLen)
            return text;
        return text.substring(0, maxLen) + "...";
    }
}