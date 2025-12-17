package com.petlog.record.client;

import com.petlog.record.dto.client.PetClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// Pet Service 호출
@FeignClient(name = "pet-service", url = "${external.pet-service.url}")
public interface PetServiceClient {

    /**
     * 펫 존재 여부 확인
     */
    @GetMapping("/api/pets/{petId}/exists")
    Boolean checkPetExists(@PathVariable("petId") Long petId);

    /**
     * 펫 상세 정보 조회
     */
    @GetMapping("/api/pets/{petId}")
    PetClientResponse getPetInfo(@PathVariable("petId") Long petId);
}