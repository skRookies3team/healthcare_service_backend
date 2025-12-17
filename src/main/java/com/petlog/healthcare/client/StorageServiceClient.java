package com.petlog.record.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

// 외부 보관함 서비스 호출 (application.yml에 external.storage-service.url 설정 필요)
@Profile("!local-test")
@FeignClient(name = "storage-service", url = "${external.storage-service.url}")
public interface StorageServiceClient {

    // 외부 보관함에 사진 저장 요청
    @PostMapping("/api/storage/photos")
    void savePhotos(@RequestBody List<PhotoRequest> photos);

    // 전송용 DTO
    record PhotoRequest(Long userId, String imageUrl) {}
}