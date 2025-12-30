package com.petlog.healthcare.config;

/**
 * AWS Bedrock 클라이언트 설정 (YAML 변수 치환 방식)
 */

import lombok.extern.slf4j.Slf4j;  // ✅ Slf4j Import
import org.springframework.beans.factory.annotation.Value;  // ✅ Value Import
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.ApiKey;
import software.amazon.awssdk.auth.credentials.ApiKeyProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;

import java.time.Duration;

@Slf4j  // ✅ Lombok Slf4j
@Configuration
public class BedrockConfig {

    /**
     * YAML → .env 자동 치환 (@Value)
     */
    @Value("${aws.bedrock.region}")
    private String region;

    @Value("${aws.bedrock.api-key}")
    private String apiKey;

    @Value("${aws.bedrock.model-id}")
    private String modelId;

    /**
     * BedrockRuntimeClient Bean (API Key 방식)
     */
    @Bean
    @Primary
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("🔥 Bedrock 초기화 - Region: {}, Model: {}", region, modelId);

        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .addApiKey(ApiKey.builder()
                                        .name("x-api-key")
                                        .value(apiKey)
                                        .build())
                                .build()
                )
                .httpClient(UrlConnectionHttpClient.builder()
                        .maxConcurrency(20)
                        .connectionTimeout(Duration.ofSeconds(30))
                        .build())
                .build();
    }
}
