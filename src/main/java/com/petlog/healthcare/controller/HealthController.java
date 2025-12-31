/*package com.petlog.healthcare.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class HealthController {

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> status = new HashMap<>();
        status.put("status", "✅ Healthcare Service 8085 - UP");
        status.put("bedrock", "✅ Claude Haiku 4.5 (ap-northeast-2)");
        status.put("port", "8085");
        status.put("kafka", "healthcare-group ready");
        return status;
    }
}*/