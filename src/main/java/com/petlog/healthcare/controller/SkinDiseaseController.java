package com.petlog.healthcare.controller;

import com.petlog.healthcare.domain.service.HealthRecordService;
import com.petlog.healthcare.dto.skindisease.SkinDiseaseResponse;
import com.petlog.healthcare.service.SkinDiseaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 반려동물 피부질환 탐지 API
 *
 * AWS Bedrock Claude Vision을 사용하여 피부 이미지 분석
 * 분석 결과는 S3에 이미지 저장 + DB에 기록 저장
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@RestController
@RequestMapping("/api/skin-disease")
@RequiredArgsConstructor
@Tag(name = "Skin Disease Detection", description = "반려동물 피부질환 탐지 API")
public class SkinDiseaseController {

    private final SkinDiseaseService skinDiseaseService;
    private final HealthRecordService healthRecordService;

    /**
     * 피부질환 이미지 분석
     *
     * POST /api/skin-disease/analyze
     *
     * @param image  반려동물 피부 이미지 (JPEG, PNG)
     * @param userId 사용자 ID (Gateway에서 전달)
     * @param petId  반려동물 ID
     * @return 분석 결과 (증상, 가능한 질환, 심각도, 권장조치)
     */
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "피부질환 분석", description = "반려동물 피부 이미지를 AI로 분석하여 잠재적 질환을 탐지합니다.")
    public ResponseEntity<SkinDiseaseResponse> analyzeImage(
            @Parameter(description = "피부 이미지 파일 (JPEG, PNG, 최대 10MB)") @RequestParam("image") MultipartFile image,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestParam(value = "petId", required = false, defaultValue = "0") Long petId) {

        log.info("═══════════════════════════════════════");
        log.info("🔬 피부질환 분석 API 호출");
        log.info("   User-Id: {}, Pet-Id: {}", userId, petId);
        log.info("   파일: {}, 크기: {} bytes",
                image.getOriginalFilename(), image.getSize());
        log.info("═══════════════════════════════════════");

        SkinDiseaseResponse response = skinDiseaseService.analyzeImage(image);

        if (response.isSuccess()) {
            log.info("✅ 분석 성공 - 심각도: {}",
                    response.getResult().getSeverity());

            // 건강 기록 저장 (userId, petId가 있을 때만)
            if (userId != null && !userId.isEmpty() && petId > 0) {
                try {
                    healthRecordService.saveSkinAnalysisRecord(
                            userId,
                            petId,
                            response.getResult().toString(),
                            response.getResult().getSeverity(),
                            response.getImageUrl());
                    log.info("💾 건강 기록 저장 완료");
                } catch (Exception e) {
                    log.warn("⚠️ 건강 기록 저장 실패: {}", e.getMessage());
                }
            }

            return ResponseEntity.ok(response);
        } else {
            log.warn("⚠️ 분석 실패: {}", response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API 상태 확인
     */
    @GetMapping("/health")
    @Operation(summary = "API 상태 확인", description = "피부질환 분석 API 정상 작동 여부 확인")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Skin Disease Detection API is UP");
    }
}
