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
 * - Persona Chat에서 Pet 성격/특성 기반 응답 생성에 활용
 *
 * 구조:
 * - User Service의 Pet Entity와 필드 매핑
 * - 필요한 최소 정보만 포함 (Over-fetching 방지)
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
     * Pet ID (Primary Key)
     */
    private Long id;

    /**
     * 반려동물 이름
     * 예: "초코", "뽀삐"
     */
    private String name;

    /**
     * 반려동물 종류
     * 예: "DOG", "CAT"
     */
    private String species;

    /**
     * 품종
     * 예: "골든 리트리버", "페르시안"
     */
    private String breed;

    /**
     * 생년월일
     */
    private LocalDate birthDate;

    /**
     * 성별
     * 예: "MALE", "FEMALE"
     */
    private String gender;

    /**
     * 체중 (kg)
     */
    private Double weight;

    /**
     * 프로필 이미지 URL
     */
    private String profileImageUrl;

    /**
     * 소유자 ID (User ID)
     */
    private Long userId;
}
