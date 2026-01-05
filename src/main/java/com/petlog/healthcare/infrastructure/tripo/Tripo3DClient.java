package com.petlog.healthcare.infrastructure.tripo;

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
 * Tripo3D.ai API 클라이언트
 * WHY: AI 기반 3D 모델 생성 (텍스트/이미지 → 3D 모델)
 *
 * @author healthcare-team
 * @since 2026-01-06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Tripo3DClient {

    @Value("${tripo3d.api-key:}")
    private String apiKey;

    @Value("${tripo3d.base-url:https://api.tripo3d.ai/v2/openapi}")
    private String baseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 텍스트로 3D 모델 생성
     *
     * @param prompt 3D 모델 설명 (예: "cute golden retriever dog")
     * @return taskId (상태 조회용)
     */
    public String generateFromText(String prompt) {
        log.info("🎨 Tripo3D 텍스트→3D 요청: {}", prompt);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "text_to_model");
            body.put("prompt", prompt);

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/task",
                    HttpMethod.POST,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String taskId = root.path("data").path("task_id").asText();

            log.info("✅ Tripo3D 작업 생성 완료: taskId={}", taskId);
            return taskId;

        } catch (Exception e) {
            log.error("❌ Tripo3D 요청 실패: {}", e.getMessage());
            throw new RuntimeException("3D 모델 생성 요청 실패", e);
        }
    }

    /**
     * 이미지로 3D 모델 생성
     *
     * @param imageUrl 이미지 URL
     * @return taskId (상태 조회용)
     */
    public String generateFromImage(String imageUrl) {
        log.info("🖼️ Tripo3D 이미지→3D 요청: {}", imageUrl);

        try {
            Map<String, Object> body = new HashMap<>();
            body.put("type", "image_to_model");
            body.put("file", Map.of("type", "url", "url", imageUrl));

            HttpHeaders headers = createHeaders();
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/task",
                    HttpMethod.POST,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            String taskId = root.path("data").path("task_id").asText();

            log.info("✅ Tripo3D 작업 생성 완료: taskId={}", taskId);
            return taskId;

        } catch (Exception e) {
            log.error("❌ Tripo3D 요청 실패: {}", e.getMessage());
            throw new RuntimeException("3D 모델 생성 요청 실패", e);
        }
    }

    /**
     * 작업 상태 조회
     *
     * @param taskId 작업 ID
     * @return 작업 상태 및 결과 URL
     */
    public Map<String, Object> getTaskStatus(String taskId) {
        log.info("📊 Tripo3D 상태 조회: taskId={}", taskId);

        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/task/" + taskId,
                    HttpMethod.GET,
                    request,
                    String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode data = root.path("data");

            Map<String, Object> result = new HashMap<>();
            result.put("taskId", taskId);
            result.put("status", data.path("status").asText());
            result.put("progress", data.path("progress").asInt());

            // 완료된 경우 모델 URL 포함
            if ("success".equals(data.path("status").asText())) {
                JsonNode output = data.path("output");
                result.put("modelUrl", output.path("model").asText());
                result.put("renderedImageUrl", output.path("rendered_image").asText());
            }

            log.info("✅ Tripo3D 상태: {}", result.get("status"));
            return result;

        } catch (Exception e) {
            log.error("❌ Tripo3D 상태 조회 실패: {}", e.getMessage());
            throw new RuntimeException("상태 조회 실패", e);
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);
        return headers;
    }
}
