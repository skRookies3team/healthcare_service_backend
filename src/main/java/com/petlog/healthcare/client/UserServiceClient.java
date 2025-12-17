package com.petlog.record.client;

import com.petlog.record.dto.client.UserClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// User Service 호출
@FeignClient(name = "user-service", url = "${external.user-service.url}")
public interface UserServiceClient {

    /**
     * 사용자 존재 여부 확인
     */
    @GetMapping("/api/users/{userId}/exists")
    Boolean checkUserExists(@PathVariable("userId") Long userId);

    /**
     * 사용자 상세 정보 조회
     */
    @GetMapping("/api/users/{userId}")
    UserClientResponse getUserInfo(@PathVariable("userId") Long userId);
}