package com.petlog.healthcare.controller;

import com.petlog.healthcare.dto.tripo.Tripo3DRequest;
import com.petlog.healthcare.dto.tripo.Tripo3DResponse;
import com.petlog.healthcare.infrastructure.tripo.Tripo3DClient;
import com.petlog.healthcare.service.Pet3DModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 3D 모델 생성 API 컨트롤러
 * WHY: Tripo3D.ai를 활용한 AI 기반 3D 펫 모델 생성
 *
 * @author healthcare-team
 * @since 2026-01-06
 */
@Slf4j
@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Tag(name = "3D Model", description = "AI 기반 3D 모델 생성 API")
public class Model3DController {

    private final Tripo3DClient tripo3DClient;
    private final Pet3DModelService pet3DModelService;

    /**
     * ⭐ 펫 ID로 3D 모델 생성 (User Service 연동)
     *
     * 플로우: petId → User Service 펫사진 조회 → Tripo3D 3D 생성
     *
     * @param petId         펫 ID
     * @param authorization JWT 토큰
     * @return taskId 및 안내 메시지
     */
    @PostMapping("/pet/{petId}")
    @Operation(summary = "펫 3D 모델 생성", description = "펫 ID로 User Service에서 사진을 가져와 3D 모델을 생성합니다")
    public ResponseEntity<Tripo3DResponse> generatePetModel(
            @PathVariable Long petId,
            @RequestHeader("Authorization") String authorization) {

        log.info("🐕 펫 3D 모델 생성 요청: petId={}", petId);

        Tripo3DResponse response = pet3DModelService.generatePet3DModel(petId, authorization);

        return ResponseEntity.ok(response);
    }

    /**
     * 텍스트로 3D 모델 생성
     *
     * @param request 프롬프트 (예: "cute golden retriever dog")
     * @return taskId 및 안내 메시지
     */
    @PostMapping("/generate-from-text")
    @Operation(summary = "텍스트→3D 모델 생성", description = "텍스트 설명으로 3D 모델을 생성합니다")
    public ResponseEntity<Tripo3DResponse> generateFromText(@RequestBody Tripo3DRequest.TextToModel request) {
        log.info("📥 3D 모델 생성 요청 (텍스트): {}", request.getPrompt());

        String taskId = tripo3DClient.generateFromText(request.getPrompt());

        Tripo3DResponse response = Tripo3DResponse.builder()
                .taskId(taskId)
                .status("queued")
                .message("3D 모델 생성이 시작되었습니다. /api/model/status/" + taskId + " 에서 상태를 확인하세요.")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 이미지로 3D 모델 생성
     *
     * @param request 이미지 URL
     * @return taskId 및 안내 메시지
     */
    @PostMapping("/generate-from-image")
    @Operation(summary = "이미지→3D 모델 생성", description = "이미지에서 3D 모델을 생성합니다")
    public ResponseEntity<Tripo3DResponse> generateFromImage(@RequestBody Tripo3DRequest.ImageToModel request) {
        log.info("📥 3D 모델 생성 요청 (이미지): {}", request.getImageUrl());

        String taskId = tripo3DClient.generateFromImage(request.getImageUrl());

        Tripo3DResponse response = Tripo3DResponse.builder()
                .taskId(taskId)
                .status("queued")
                .message("3D 모델 생성이 시작되었습니다. /api/model/status/" + taskId + " 에서 상태를 확인하세요.")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 작업 상태 조회
     *
     * @param taskId 작업 ID
     * @return 상태 및 결과 URL
     */
    @GetMapping("/status/{taskId}")
    @Operation(summary = "3D 모델 생성 상태 조회", description = "생성 진행 상태와 완료 시 다운로드 URL을 반환합니다")
    public ResponseEntity<Tripo3DResponse> getStatus(@PathVariable String taskId) {
        log.info("📊 3D 모델 상태 조회: {}", taskId);

        Map<String, Object> status = tripo3DClient.getTaskStatus(taskId);

        Tripo3DResponse response = Tripo3DResponse.builder()
                .taskId((String) status.get("taskId"))
                .status((String) status.get("status"))
                .progress((Integer) status.get("progress"))
                .modelUrl((String) status.get("modelUrl"))
                .renderedImageUrl((String) status.get("renderedImageUrl"))
                .build();

        return ResponseEntity.ok(response);
    }
}
