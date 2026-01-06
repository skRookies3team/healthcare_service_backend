package com.petlog.healthcare.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Pet 정보 응답 DTO
 *
 * WHY?
 * - User Service에서 조회한 Pet 정보를 담는 DTO
 * - Feign Client 응답 매핑용
 * - User Service의 PetResponse.GetPetDto와 필드명 일치
 *
 * @author healthcare-team
 * @since 2026-01-05
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PetInfoResponse {

    /**
     * Pet ID (User Service: petId)
     */
    private Long petId;

    /**
     * 반려동물 이름 (User Service: petName)
     */
    private String petName;

    /**
     * 반려동물 종류 (User Service: species - Enum)
     * 예: "DOG", "CAT"
     */
    private String species;

    /**
     * 품종 (User Service: breed)
     */
    private String breed;

    /**
     * 성별 (User Service: genderType - Enum)
     */
    private String genderType;

    /**
     * 나이 (User Service: age)
     */
    private Integer age;

    /**
     * 생년월일 (User Service: birth)
     */
    private LocalDate birth;

    /**
     * 프로필 이미지 URL (User Service: profileImage)
     */
    private String profileImage;

    /**
     * 중성화 여부 (User Service: neutered)
     */
    private boolean neutered;

    /**
     * 예방접종 여부 (User Service: vaccinated)
     */
    private boolean vaccinated;

    /**
     * 상태 (User Service: status - Enum)
     */
    private String status;

    // ====== Helper Methods ======

    /**
     * 이름 반환 (편의 메서드)
     */
    public String getName() {
        return this.petName;
    }

    /**
     * 프로필 이미지 URL 반환 (편의 메서드)
     */
    public String getProfileImageUrl() {
        return this.profileImage;
    }
}
