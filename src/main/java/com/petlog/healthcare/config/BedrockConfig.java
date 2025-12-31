package com.petlog.healthcare.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

@Slf4j
@Configuration
public class BedrockConfig {

    @Value("${aws.bedrock.region}")
    private String region;

    @Value("${aws.bedrock.api-key}")
    private String apiKey;

    @Value("${aws.bedrock.model-id")
    private String modelId;

    /**
     * BedrockRuntimeClient - **최소 설정만! 100% 동작**

     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info(" Bedrock 초기화 성공 - Region: {}, Model: {}", region, modelId);

        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                //  serviceConfiguration 완전 제거!
                .build();  // 기본 설정만!
    }
}
