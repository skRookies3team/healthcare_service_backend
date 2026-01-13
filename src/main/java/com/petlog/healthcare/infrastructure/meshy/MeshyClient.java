package com.petlog.healthcare.infrastructure.meshy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Meshy.ai API 클라이언트
 * WHY: Meshy.ai API 호출을 위한 3D 모델 생성
 * 
 * API 문서: https://docs.meshy.ai
 * 
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MeshyClient {

    @Value("${meshy.api-key:}")
    private String apiKey;

    @Value("${meshy.base-url:https://api.meshy.ai}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 이미지로 3D 모델 생성 (텍스처 포함)
     *
     * @param imageUrl 이미지 URL 또는 Base64 Data URI (data:image/...)
     * @return taskId (상태 조회용)
     */
    public String generateFromImage(String imageUrl) {
        log.info("🖼️ Meshy 이미지→3D 요청: {}",
                imageUrl.startsWith("data:") ? "Base64 이미지 (길이: " + imageUrl.length() + ")" : imageUrl);
        validateApiKey();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("image_url", imageUrl); // ⭐ URL 또는 Base64 Data URI 둘 다 지원!
            body.put("ai_model", "meshy-5"); // ⭐ meshy-5 사용 (Retexture 호환!)
            // ⚠️ meshy-6 (latest)는 Retexture API와 호환 안 됨!
            body.put("enable_pbr", true); // PBR 맵 생성
            body.put("should_remesh", true); // 메시 최적화

            // 토폴로지 옵션
            body.put("topology", "quad");
            body.put("target_polycount", 30000);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.debug("📤 Meshy API 요청 전송...");

            // v1 API 사용 (latest 모델로 텍스처 지원)
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/image-to-3d",
                    HttpMethod.POST,
                    request,
                    String.class);

            log.debug("📥 Meshy API 응답: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            String taskId = root.path("result").asText();

            log.info("✅ Meshy 작업 생성 완료: taskId={}", taskId);
            return taskId;

        } catch (Exception e) {
            log.error("❌ Meshy 요청 실패: {}", e.getMessage());
            throw new RuntimeException("3D 모델 생성 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * ⭐ Preview 모델에 텍스처 적용 (Retexture API)
     * WHY: Preview는 형태만, Retexture는 원본 이미지 기반 텍스처 적용
     *
     * ⚠️ 주의: model_url 사용 (input_task_id는 meshy-4/5만 지원)
     *
     * @param previewModelUrl  Preview GLB 모델 URL
     * @param originalImageUrl 원본 이미지 URL (텍스처 스타일용)
     * @return retextureTaskId (새로운 taskId)
     */
    public String retextureWithModelUrl(String previewModelUrl, String originalImageUrl) {
        log.info("🎨 Meshy Retexture 요청 (model_url): modelUrl={}", previewModelUrl);
        log.info("🎨 Meshy Retexture 요청 (image_style): imageUrl={}", originalImageUrl);
        validateApiKey();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("model_url", previewModelUrl); // ⭐ GLB URL 직접 사용 (meshy-6 호환)
            body.put("image_style_url", originalImageUrl); // ⭐ 원본 이미지로 텍스처 적용
            body.put("enable_original_uv", true);
            body.put("enable_pbr", true); // PBR 맵 생성

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("📤 Meshy Retexture 요청 바디: {}", body);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/retexture",
                    HttpMethod.POST,
                    request,
                    String.class);

            log.info("📥 Meshy Retexture 응답: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            String retextureTaskId = root.path("result").asText();

            log.info("✅ Meshy Retexture 시작: retextureTaskId={}", retextureTaskId);
            return retextureTaskId;

        } catch (Exception e) {
            log.error("❌ Meshy Retexture 실패: {}", e.getMessage());
            throw new RuntimeException("Retexture 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * ⭐ meshy-5 Preview에 텍스처 적용 (input_task_id 방식)
     * WHY: meshy-5로 생성된 Preview는 input_task_id 방식이 공식 지원됨
     *
     * @param previewTaskId    Preview 단계에서 받은 taskId (meshy-5)
     * @param originalImageUrl 원본 이미지 URL (텍스처 스타일용)
     * @return retextureTaskId (새로운 taskId)
     */
    public String retextureWithTaskId(String previewTaskId, String originalImageUrl) {
        log.info("🎨 Meshy Retexture 요청 (input_task_id): taskId={}", previewTaskId);
        log.info("🎨 Meshy Retexture 스타일 이미지: {}", originalImageUrl);
        validateApiKey();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("input_task_id", previewTaskId); // ⭐ meshy-5 task ID
            body.put("image_style_url", originalImageUrl); // ⭐ 원본 이미지로 텍스처
            body.put("enable_original_uv", true);
            body.put("enable_pbr", true);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("📤 Meshy Retexture 요청 바디: {}", body);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/retexture",
                    HttpMethod.POST,
                    request,
                    String.class);

            log.info("📥 Meshy Retexture 응답: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            String retextureTaskId = root.path("result").asText();

            log.info("✅ Meshy Retexture 작업 생성: retextureTaskId={}", retextureTaskId);
            return retextureTaskId;

        } catch (Exception e) {
            log.error("❌ Meshy Retexture 실패: {}", e.getMessage());
            throw new RuntimeException("Retexture 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * @deprecated retextureWithTaskId 사용 권장
     */
    @Deprecated
    public String retextureWithImage(String previewTaskId, String originalImageUrl) {
        return retextureWithTaskId(previewTaskId, originalImageUrl);
    }

    /**
     * ⭐ Preview 모델 Refine (텍스처 적용) - Image-to-3D Refine API
     * WHY: Image-to-3D의 Preview를 Refine하여 텍스처를 생성하는 공식 방법
     *
     * @param previewTaskId Preview 작업 ID
     * @param imageUrl      원본 이미지 URL (텍스처 가이드용)
     * @return refineTaskId
     */
    public String refinePreview(String previewTaskId, String imageUrl) {
        log.info("🎨 Meshy Refine 요청: previewTaskId={}", previewTaskId);
        validateApiKey();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("mode", "refine"); // ⭐ Refine 모드!
            body.put("image_url", imageUrl); // ⭐ 필수 필드!
            body.put("preview_task_id", previewTaskId); // Preview task ID
            body.put("enable_pbr", true); // PBR 맵 생성

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            log.info("📤 Meshy Refine 요청 바디: {}", body);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/image-to-3d", // ⭐ 같은 엔드포인트, mode=refine
                    HttpMethod.POST,
                    request,
                    String.class);

            log.info("📥 Meshy Refine 응답: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());
            String refineTaskId = root.path("result").asText();

            log.info("✅ Meshy Refine 작업 생성: refineTaskId={}", refineTaskId);
            return refineTaskId;

        } catch (Exception e) {
            log.error("❌ Meshy Refine 실패: {}", e.getMessage());
            throw new RuntimeException("Refine 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * Preview 모델에 텍스처 적용 (텍스트 프롬프트 사용)
     */
    public String retextureWithText(String previewTaskId, String textPrompt) {
        log.info("🎨 Meshy Retexture (텍스트) 요청: previewTaskId={}, prompt={}", previewTaskId, textPrompt);
        validateApiKey();

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("input_task_id", previewTaskId);
            body.put("text_style_prompt", textPrompt); // ⭐ 텍스트로 텍스처 스타일 지정
            body.put("enable_original_uv", true);
            body.put("enable_pbr", true);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/retexture",
                    HttpMethod.POST,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String retextureTaskId = root.path("result").asText();

            log.info("✅ Meshy Retexture (텍스트) 시작: retextureTaskId={}", retextureTaskId);
            return retextureTaskId;

        } catch (Exception e) {
            log.error("❌ Meshy Retexture 실패: {}", e.getMessage());
            throw new RuntimeException("Retexture 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * @deprecated 기존 refine 메서드 - retextureWithImage 사용 권장
     */
    @Deprecated
    public String refinePreviewTask(String previewTaskId) {
        log.warn("⚠️ refinePreviewTask는 더 이상 사용되지 않습니다. retextureWithImage를 사용하세요.");
        // 이미지 URL 없이는 Retexture 불가 - 텍스트 프롬프트로 대체
        return retextureWithText(previewTaskId, "realistic pet texture, detailed fur, natural colors");
    }

    /**
     * API Key 검증
     */
    private void validateApiKey() {
        log.info("🔑 API Key 상태: {}", apiKey != null && !apiKey.isEmpty()
                ? "설정됨 (길이: " + apiKey.length() + ")"
                : "❌ 미설정!");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Meshy API Key가 설정되지 않았습니다. .env 파일에 MESHY_API_KEY를 설정하세요.");
        }
    }

    /**
     * 텍스트로 3D 모델 생성
     *
     * @param prompt 3D 모델 설명
     * @return taskId (상태 조회용)
     */
    public String generateFromText(String prompt) {
        log.info("🎨 Meshy 텍스트→3D 요청: {}", prompt);
        log.info("🔑 API Key 상태: {}", apiKey != null && !apiKey.isEmpty()
                ? "설정됨 (길이: " + apiKey.length() + ")"
                : "❌ 미설정!");

        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("Meshy API Key가 설정되지 않았습니다.");
        }

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("mode", "preview"); // preview 또는 refine
            body.put("prompt", prompt);
            body.put("art_style", "realistic");
            body.put("negative_prompt", "low quality, blurry");

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/text-to-3d",
                    HttpMethod.POST,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String taskId = root.path("result").asText();

            log.info("✅ Meshy 작업 생성 완료: taskId={}", taskId);
            return taskId;

        } catch (Exception e) {
            log.error("❌ Meshy 요청 실패: {}", e.getMessage());
            throw new RuntimeException("3D 모델 생성 요청 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 작업 상태 조회
     *
     * @param taskId 작업 ID
     * @return 작업 상태 및 결과 URL
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        log.info("📊 Meshy 상태 조회: taskId={}", taskId);

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            // v1 API 사용
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/image-to-3d/" + taskId,
                    HttpMethod.GET,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", root.path("status").asText());
            result.put("progress", root.path("progress").asInt());

            // 완료된 경우 모델 URL 포함
            String status = root.path("status").asText();
            if ("SUCCEEDED".equals(status)) {
                result.put("modelUrl", root.path("model_urls").path("glb").asText());
                result.put("thumbnailUrl", root.path("thumbnail_url").asText());
            }

            log.info("✅ Meshy 상태: {} ({}%)", status, result.get("progress"));
            return result;

        } catch (Exception e) {
            log.error("❌ Meshy 상태 조회 실패: {}", e.getMessage());
            throw new RuntimeException("상태 조회 실패", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);
        return headers;
    }

    /**
     * Retexture 작업 상태 조회
     * WHY: Retexture API는 별도 엔드포인트 사용
     *
     * @param taskId Retexture 작업 ID
     * @return 작업 상태 및 결과 URL
     */
    public Map<String, Object> getRetextureStatus(String taskId) {
        log.info("📊 Meshy Retexture 상태 조회: taskId={}", taskId);

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/openapi/v1/retexture/" + taskId, // ⭐ Retexture 상태 조회
                    HttpMethod.GET,
                    request,
                    String.class);

            // ⭐ 디버그: 전체 응답 로깅
            log.debug("📥 Retexture API 응답: {}", response.getBody());

            JsonNode root = objectMapper.readTree(response.getBody());

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", root.path("status").asText());
            result.put("progress", root.path("progress").asInt());

            String status = root.path("status").asText();
            if ("SUCCEEDED".equals(status)) {
                String modelUrl = root.path("model_urls").path("glb").asText();
                String thumbnailUrl = root.path("thumbnail_url").asText();

                // ⭐ 디버그: 추출된 URL 로깅
                log.info("🎨 Retexture 완료 - modelUrl: {}", modelUrl);
                log.info("🎨 Retexture 완료 - thumbnailUrl: {}", thumbnailUrl);

                result.put("modelUrl", modelUrl);
                result.put("thumbnailUrl", thumbnailUrl);

                // ⭐ 텍스처 URL도 추가 (디버그용)
                if (root.has("texture_urls") && root.path("texture_urls").isArray()) {
                    JsonNode textureUrls = root.path("texture_urls").get(0);
                    if (textureUrls != null) {
                        result.put("baseColorUrl", textureUrls.path("base_color").asText());
                        log.info("🎨 Retexture 텍스처 - base_color: {}", textureUrls.path("base_color").asText());
                    }
                }
            }

            log.info("✅ Meshy Retexture 상태: {} ({}%)", status, result.get("progress"));
            return result;

        } catch (Exception e) {
            log.error("❌ Meshy Retexture 상태 조회 실패: {}", e.getMessage());
            throw new RuntimeException("Retexture 상태 조회 실패", e);
        }
    }
}
