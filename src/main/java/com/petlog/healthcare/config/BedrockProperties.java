package com.petlog.healthcare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aws.bedrock")
public class BedrockProperties {
    private String apiKey;
    private String region = "ap-northeast-2";
    private String modelId;       // Sonnet ID
    private String haikuModelId;  // Haiku ID
    private int maxTokens = 2000;
    private Double temperature = 0.7;
}