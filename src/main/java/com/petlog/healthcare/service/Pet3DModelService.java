package com.petlog.healthcare.service;

import com.petlog.healthcare.client.SocialServiceClient;
import com.petlog.healthcare.client.SocialServiceClient.SliceResponse;
import com.petlog.healthcare.client.UserServiceClient;
import com.petlog.healthcare.client.dto.FeedDto;
import com.petlog.healthcare.client.dto.PetInfoResponse;
import com.petlog.healthcare.dto.meshy.Meshy3DResponse;
import com.petlog.healthcare.infrastructure.meshy.MeshyClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pet 3D 모델 생성 서비스
 * 
 * 플로우:
 * 1. petId로 User Service에서 펫 정보 조회
 * 2. Social Service에서 유저 피드의 이미지 목록 조회
 * 3. 프로필 이미지 또는 피드 이미지 중 랜덤 선택
 * 4. Meshy.ai API로 3D 모델 생성 요청
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class Pet3DModelService {

    private final UserServiceClient userServiceClient;
    private final SocialServiceClient socialServiceClient;
    private final MeshyClient meshyClient;

    private final Random random = new Random();

    /**
     * 펫 ID로 3D 모델 생성
     *
     * @param petId         펫 ID
     * @param userId        유저 ID
     * @param authorization JWT 토큰
     * @return 3D 모델 생성 응답 (taskId 포함)
     */
    public Meshy3DResponse generatePet3DModel(Long petId, String userId, String authorization) {
        log.info("🐕 펫 3D 모델 생성 시작: petId={}, userId={}", petId, userId);

        // 1. User Service에서 펫 정보 조회
        PetInfoResponse petInfo = userServiceClient.getPetInfo(petId, authorization);
        log.info("✅ 펫 정보 조회 완료: name={}, species={}", petInfo.getName(), petInfo.getSpecies());

        // 2. 사용 가능한 이미지 수집 (프로필 + 피드)
        List<String> availableImages = collectAvailableImages(petInfo, userId);

        // 3. 이미지 선택 및 3D 생성
        if (availableImages.isEmpty()) {
            log.warn("⚠️ 사용 가능한 이미지 없음 - 텍스트로 생성");
            return generateFromText(petInfo);
        }

        // 랜덤 이미지 선택
        String selectedImage = availableImages.get(random.nextInt(availableImages.size()));
        log.info("🎲 랜덤 이미지 선택: {} (총 {}개 중)", selectedImage, availableImages.size());

        return generateFromImage(petInfo, selectedImage);
    }

    /**
     * 사용 가능한 이미지 수집 (프로필 + 피드)
     */
    private List<String> collectAvailableImages(PetInfoResponse petInfo, String userId) {
        List<String> images = new ArrayList<>();

        // 1. 프로필 이미지 추가
        String profileImage = petInfo.getProfileImageUrl();
        if (profileImage != null && !profileImage.isBlank()) {
            images.add(profileImage);
            log.info("📸 프로필 이미지 추가: {}", profileImage);
        }

        // 2. Social Service 피드 이미지 조회
        try {
            SliceResponse<FeedDto> feeds = socialServiceClient.getUserFeeds(userId, userId, 0, 20);

            if (feeds != null && feeds.content() != null) {
                for (FeedDto feed : feeds.content()) {
                    if (feed.getImageUrls() != null) {
                        for (String imageUrl : feed.getImageUrls()) {
                            if (imageUrl != null && !imageUrl.isBlank()) {
                                images.add(imageUrl);
                            }
                        }
                    }
                }
                log.info("📷 피드 이미지 {}개 수집 (피드 {}개)",
                        images.size() - (profileImage != null ? 1 : 0),
                        feeds.content().size());
            }
        } catch (Exception e) {
            log.warn("⚠️ Social Service 피드 조회 실패 (무시): {}", e.getMessage());
        }

        log.info("📦 총 수집된 이미지: {}개", images.size());
        return images;
    }

    /**
     * 이미지로 3D 모델 생성
     */
    private Meshy3DResponse generateFromImage(PetInfoResponse petInfo, String imageUrl) {
        log.info("🖼️ 이미지로 3D 모델 생성: {}", imageUrl);
        String taskId = meshyClient.generateFromImage(imageUrl);

        return Meshy3DResponse.builder()
                .taskId(taskId)
                .status("queued")
                .message("펫 '" + petInfo.getName() + "'의 3D 모델 생성이 시작되었습니다.")
                .sourceImageUrl(imageUrl)
                .build();
    }

    /**
     * 텍스트로 3D 모델 생성 (이미지 없을 때)
     */
    private Meshy3DResponse generateFromText(PetInfoResponse petInfo) {
        String prompt = buildPromptFromPetInfo(petInfo);
        String taskId = meshyClient.generateFromText(prompt);

        return Meshy3DResponse.builder()
                .taskId(taskId)
                .status("queued")
                .message("펫 '" + petInfo.getName() + "'의 3D 모델 생성이 시작되었습니다. (텍스트 기반)")
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
