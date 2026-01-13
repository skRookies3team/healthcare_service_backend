package com.petlog.healthcare.client;

import com.petlog.healthcare.client.dto.FeedDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Social Service Feign Client
 *
 * 피드 이미지 조회용 (3D 모델 생성 시 사용)
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@FeignClient(name = "social-service", url = "${SOCIAL_SERVICE_URL:http://localhost:8083}", path = "/api")
public interface SocialServiceClient {

        /**
         * 유저별 피드 조회 (마이페이지)
         * 피드에서 이미지 URL을 추출하여 3D 모델 생성에 사용
         *
         * @param targetUserId 조회할 유저 ID
         * @param viewerId     조회하는 유저 ID (같은 유저면 동일)
         * @return 피드 목록 (imageUrls 포함)
         */
        @GetMapping("/feeds/user/{targetUserId}/viewer/{viewerId}")
        SliceResponse<FeedDto> getUserFeeds(
                        @PathVariable("targetUserId") String targetUserId,
                        @PathVariable("viewerId") String viewerId,
                        @RequestParam(value = "page", defaultValue = "0") int page,
                        @RequestParam(value = "size", defaultValue = "10") int size);

        /**
         * 페이징된 응답 래퍼
         */
        record SliceResponse<T>(
                        List<T> content,
                        boolean hasNext,
                        int number,
                        int size) {
        }
}
