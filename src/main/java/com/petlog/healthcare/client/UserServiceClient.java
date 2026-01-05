package com.petlog.healthcare.client;

import com.petlog.healthcare.client.dto.PetInfoResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * User Service Feign Client
 *
 * Pet 정보 조회용
 *
 * WHY Feign?
 * - MSA 간 동기 호출 표준 방식
 * - Gateway를 통한 JWT 전달 자동화
 * - Retry, Timeout 설정 용이
 *
 * @author healthcare-team
 * @since 2026-01-03
 */
@FeignClient(
        name = "user-service",
        url = "${USER_SERVICE_URL:http://localhost:8080}",
        path = "/api"
)
public interface UserServiceClient {

    /**
     * Pet 정보 조회
     *
     * @param petId Pet ID
     * @param authorization JWT 토큰 (Gateway에서 전달받음)
     * @return Pet 정보
     */
    @GetMapping("/pets/{petId}")
    PetInfoResponse getPetInfo(
            @PathVariable("petId") Long petId,
            @RequestHeader("Authorization") String authorization
    );
}