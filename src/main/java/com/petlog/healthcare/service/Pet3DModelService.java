package com.petlog.healthcare.service;

import com.petlog.healthcare.client.UserServiceClient;
import com.petlog.healthcare.client.dto.PetInfoResponse;
import com.petlog.healthcare.dto.tripo.Tripo3DResponse;
import com.petlog.healthcare.infrastructure.tripo.Tripo3DClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Pet 3D 모델 생성 서비스
 * WHY: User Service에서 펫 사진을 가져와 Tripo3D로 3D 모델 생성
 *
 * 플로우:
 * 1. petId로 User Service에서 펫 정보 조회
 * 2. 펫 프로필 이미지 URL 획득
 * 3. Tripo3D API로 3D 모델 생성 요청
 * 4. taskId 반환 (Frontend에서 상태 폴링)
 *
 * @author healthcare-team
 * @since 2026-01-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Pet3DModelService {

    private final UserServiceClient userServiceClient;
    private final Tripo3DClient tripo3DClient;

    /**
     * 펫 ID로 3D 모델 생성
     *
     * @param petId         펫 ID
     * @param authorization JWT 토큰
     * @return 3D 모델 생성 응답 (taskId 포함)
     */
    public Tripo3DResponse generatePet3DModel(Long petId, String authorization) {
        log.info("🐕 펫 3D 모델 생성 시작: petId={}", petId);

        // 1. User Service에서 펫 정보 조회
        PetInfoResponse petInfo = userServiceClient.getPetInfo(petId, authorization);
        log.info("✅ 펫 정보 조회 완료: name={}, species={}", petInfo.getName(), petInfo.getSpecies());

        // 2. 프로필 이미지 URL 확인
        String imageUrl = petInfo.getProfileImageUrl();
        if (imageUrl == null || imageUrl.isBlank()) {
            log.warn("⚠️ 펫 프로필 이미지 없음 - 텍스트로 생성: {}", petInfo.getName());
            // 이미지 없으면 텍스트 프롬프트로 생성
            String prompt = buildPromptFromPetInfo(petInfo);
            String taskId = tripo3DClient.generateFromText(prompt);

            return Tripo3DResponse.builder()
                    .taskId(taskId)
                    .status("queued")
                    .message("펫 '" + petInfo.getName() + "'의 3D 모델 생성이 시작되었습니다. (텍스트 기반)")
                    .build();
        }

        // 3. 이미지로 3D 모델 생성
        log.info("🖼️ 이미지로 3D 모델 생성: {}", imageUrl);
        String taskId = tripo3DClient.generateFromImage(imageUrl);

        return Tripo3DResponse.builder()
                .taskId(taskId)
                .status("queued")
                .message("펫 '" + petInfo.getName() + "'의 3D 모델 생성이 시작되었습니다. (이미지 기반)")
                .build();
    }

    /**
     * 펫 정보로 텍스트 프롬프트 생성
     */
    private String buildPromptFromPetInfo(PetInfoResponse petInfo) {
        StringBuilder prompt = new StringBuilder("A cute ");

        // 종류
        if ("DOG".equalsIgnoreCase(petInfo.getSpecies())) {
            prompt.append("dog");
        } else if ("CAT".equalsIgnoreCase(petInfo.getSpecies())) {
            prompt.append("cat");
        } else {
            prompt.append("pet");
        }

        // 품종
        if (petInfo.getBreed() != null && !petInfo.getBreed().isBlank()) {
            prompt.append(", ").append(petInfo.getBreed()).append(" breed");
        }

        prompt.append(", 3D model, high quality, detailed fur texture");

        log.info("📝 생성된 프롬프트: {}", prompt);
        return prompt.toString();
    }
}
