package com.petlog.healthcare.service;

import com.petlog.healthcare.dto.meshy.Meshy3DResponse;
import com.petlog.healthcare.infrastructure.meshy.MeshyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

/**
 * Meshy 3D 모델 자동 Refine 서비스 (동기식)
 * WHY: Preview 완료 후 자동으로 Refine까지 대기하여 텍스처 완성
 *
 * @author healthcare-team
 * @since 2026-01-08
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MeshyAutoRefineService {

    private final MeshyClient meshyClient;
    private final S3ImageService s3ImageService; // ⭐ S3 업로드용
    private final RestTemplate restTemplate; // ⭐ GLB 다운로드용

    /**
     * ⭐ 이미지로 3D 모델 생성 + 자동 Refine (동기식)
     * WHY: Preview → Refine → 최종 모델까지 한 번에 완료
     *
     * @param imageUrl 원본 이미지 URL
     * @return 최종 완성된 3D 모델 응답 (텍스처 포함)
     */
    public Meshy3DResponse generateWithAutoRefine(String imageUrl) {
        log.info("🚀 Auto-Refine 3D 생성 시작: {}", imageUrl);

        try {
            // ============================================
            // Step 1: Preview 요청
            // ============================================
            String previewTaskId = meshyClient.generateFromImage(imageUrl);
            log.info("✅ Preview 시작: {}", previewTaskId);

            // ============================================
            // Step 2: Preview 완료 대기 (최대 5분)
            // ============================================
            log.info("⏳ Preview 완료 대기 중...");
            Map<String, Object> previewResult = waitForCompletion(previewTaskId, 60, "Preview");

            if (!"SUCCEEDED".equals(previewResult.get("status"))) {
                log.error("❌ Preview 실패: {}", previewResult.get("status"));
                return Meshy3DResponse.builder()
                        .taskId(previewTaskId)
                        .status("FAILED")
                        .message("Preview 생성 실패: " + previewResult.get("status"))
                        .build();
            }

            log.info("✅ Preview 완료! taskId={}", previewTaskId);

            // ============================================
            // Step 3: Refine 요청 (mode:refine - 텍스처 생성!)
            // ============================================
            log.info("🎨 Refine 시작 (mode:refine - 공식 텍스처 생성 방법)...");
            String refineTaskId = meshyClient.refinePreview(previewTaskId, imageUrl);
            log.info("🎨 Refine 시작됨: previewTaskId={} → refineTaskId={}", previewTaskId, refineTaskId);

            // ============================================
            // Step 4: Refine 완료 대기 (최대 10분) - Image-to-3D 상태 조회 사용
            // ============================================
            log.info("⏳ Refine (텍스처 적용) 완료 대기 중...");
            Map<String, Object> refineResult = waitForCompletion(refineTaskId, 120, "Refine"); // ⭐ Image-to-3D 상태 조회

            if (!"SUCCEEDED".equals(refineResult.get("status"))) {
                log.error("❌ Refine 실패: {}", refineResult.get("status"));
                return Meshy3DResponse.builder()
                        .taskId(refineTaskId)
                        .status("FAILED")
                        .message("텍스처 적용 실패: " + refineResult.get("status"))
                        .build();
            }

            String meshyModelUrl = (String) refineResult.get("modelUrl");
            String thumbnailUrl = (String) refineResult.get("thumbnailUrl");
            log.info("🎉 텍스처 적용 완료! 최종 모델 URL: {}", meshyModelUrl);

            // ============================================
            // Step 5: ⭐ GLB 파일을 S3에 저장 (CORS 문제 해결!)
            // ============================================
            String s3ModelUrl = uploadGlbToS3(meshyModelUrl, refineTaskId);
            log.info("✅ S3 업로드 완료: {}", s3ModelUrl);

            // ============================================
            // Step 6: 최종 결과 반환 (S3 URL 사용!)
            // ============================================
            return Meshy3DResponse.builder()
                    .taskId(refineTaskId)
                    .status("SUCCEEDED")
                    .progress(100)
                    .modelUrl(s3ModelUrl) // ⭐ Meshy URL 대신 S3 URL 반환!
                    .renderedImageUrl(thumbnailUrl)
                    .message("🎉 3D 모델 생성 완료! 텍스처가 적용되었습니다.")
                    .build();

        } catch (Exception e) {
            log.error("❌ Auto-Refine 실패: {}", e.getMessage(), e);
            return Meshy3DResponse.builder()
                    .status("FAILED")
                    .message("3D 모델 생성 실패: " + e.getMessage())
                    .build();
        }
    }

    /**
     * ⭐ 외부 GLB URL을 다운로드하여 S3에 업로드
     * WHY: Meshy AI GLB URL은 CORS 미지원 → S3에 저장하여 프론트엔드에서 로드 가능
     *
     * @param externalGlbUrl Meshy AI GLB URL
     * @param taskId         작업 ID (파일명에 사용)
     * @return S3 URL (실패 시 원본 URL 반환)
     */
    private String uploadGlbToS3(String externalGlbUrl, String taskId) {
        try {
            log.info("📥 GLB 다운로드 시작: {}", externalGlbUrl);

            // 1. 외부 URL에서 GLB 파일 다운로드
            byte[] glbBytes = restTemplate.getForObject(externalGlbUrl, byte[].class);

            if (glbBytes == null || glbBytes.length == 0) {
                log.warn("⚠️ GLB 다운로드 실패 - 원본 URL 반환");
                return externalGlbUrl;
            }

            log.info("📥 GLB 다운로드 완료: {} bytes", glbBytes.length);

            // 2. S3에 업로드
            String filename = taskId + "_" + UUID.randomUUID().toString().substring(0, 8) + ".glb";
            String s3Url = s3ImageService.uploadBytes(glbBytes, "3d-models", filename, "model/gltf-binary");

            if (s3Url != null) {
                return s3Url;
            } else {
                log.warn("⚠️ S3 업로드 실패 - 원본 URL 반환");
                return externalGlbUrl;
            }

        } catch (Exception e) {
            log.error("❌ GLB S3 업로드 실패: {} - 원본 URL 반환", e.getMessage());
            // 실패 시 원본 URL 반환 (폴백)
            return externalGlbUrl;
        }
    }

    /**
     * 작업 완료 대기 (폴링)
     *
     * @param taskId      작업 ID
     * @param maxAttempts 최대 시도 횟수 (5초 간격)
     * @param phase       단계명 (로그용)
     * @return 최종 상태
     */
    private Map<String, Object> waitForCompletion(String taskId, int maxAttempts, String phase)
            throws InterruptedException {

        int attempts = 0;

        while (attempts < maxAttempts) {
            Thread.sleep(5000); // 5초 대기
            attempts++;

            Map<String, Object> status = meshyClient.getTaskStatus(taskId);
            String currentStatus = (String) status.get("status");
            Integer progress = (Integer) status.get("progress");

            log.info("📊 {} 상태: {} ({}%) - 시도 {}/{}",
                    phase, currentStatus, progress, attempts, maxAttempts);

            if ("SUCCEEDED".equals(currentStatus)) {
                return status;
            }

            if ("FAILED".equals(currentStatus) || "EXPIRED".equals(currentStatus)) {
                return status;
            }
        }

        // 타임아웃
        return Map.of("status", "TIMEOUT", "taskId", taskId);
    }

    /**
     * Retexture 작업 완료 대기 (폴링)
     * WHY: Retexture API는 별도 엔드포인트 사용
     */
    private Map<String, Object> waitForRetextureCompletion(String taskId, int maxAttempts, String phase)
            throws InterruptedException {

        int attempts = 0;

        while (attempts < maxAttempts) {
            Thread.sleep(5000); // 5초 대기
            attempts++;

            Map<String, Object> status = meshyClient.getRetextureStatus(taskId); // ⭐ Retexture 상태 조회
            String currentStatus = (String) status.get("status");
            Integer progress = (Integer) status.get("progress");

            log.info("📊 {} 상태: {} ({}%) - 시도 {}/{}",
                    phase, currentStatus, progress, attempts, maxAttempts);

            if ("SUCCEEDED".equals(currentStatus)) {
                return status;
            }

            if ("FAILED".equals(currentStatus) || "EXPIRED".equals(currentStatus)) {
                return status;
            }
        }

        // 타임아웃
        return Map.of("status", "TIMEOUT", "taskId", taskId);
    }

    /**
     * Preview만 요청 (비동기 폴링용)
     */
    public String generatePreviewOnly(String imageUrl) {
        log.info("🖼️ Preview Only 요청: {}", imageUrl);
        return meshyClient.generateFromImage(imageUrl);
    }

    /**
     * 상태 조회 (단순)
     */
    public Map<String, Object> getFinalStatus(String taskId) {
        return meshyClient.getTaskStatus(taskId);
    }
}
