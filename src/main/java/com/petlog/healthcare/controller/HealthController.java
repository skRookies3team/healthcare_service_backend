/*package com.petlog.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    private final BedrockRuntimeClient bedrockClient;

    @GetMapping("api/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "✅ Healthcare Service 8085 - UP");
        status.put("bedrock", "✅ BedrockRuntimeClient 등록됨");
        status.put("port", "8085");
        status.put("kafka", "healthcare-group ready");
        return status;
    }
}
*/