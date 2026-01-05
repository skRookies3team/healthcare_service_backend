package com.petlog.healthcare.dto.tripo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tripo3D.ai 3D 모델 생성 요청 DTO
 */
public class Tripo3DRequest {

    /**
     * 텍스트로 3D 모델 생성 요청
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TextToModel {
        private String prompt; // 3D 모델 설명 (예: "cute golden retriever dog")
    }

    /**
     * 이미지로 3D 모델 생성 요청
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImageToModel {
        private String imageUrl; // 이미지 URL
    }
}
