package com.petlog.healthcare.dto.tripo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tripo3D.ai 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tripo3DResponse {

    private String taskId; // 작업 ID
    private String status; // queued, running, success, failed
    private Integer progress; // 진행률 (0-100)
    private String modelUrl; // 3D 모델 다운로드 URL (성공 시)
    private String renderedImageUrl; // 렌더링된 이미지 URL (성공 시)
    private String message; // 안내 메시지
}
