package com.petlog.healthcare.config;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

/**
 * AWS Bedrock Long-term API Key 설정
 *
 * 리전: ap-northeast-2 (한국)
 * 모델: Claude Haiku 4.5 (anthropic.claude-haiku-4-5-20251001-v1:0)
 * 인증: Bearer Token 방식
 *
 * @author healthcare-team
 * @since 2025-12-31
 */
@Slf4j
@Configuration
public class BedrockConfig {

    @Value("${AWS_BEDROCK_API_KEY}")
    private String apiKey;

    @Value("${AWS_BEDROCK_REGION}")
    private String region;

    @Value("${AWS_BEDROCK_MODEL_ID}")
    private String modelId;

    @Value("${AWS_BEDROCK_MAX_TOKENS}")
    private int maxTokens;

    @Bean
    public BedrockProperties bedrockProperties() {
        log.info("===========================================");
        log.info(" Bedrock API Key 설정 완료");
        log.info("===========================================");
        log.info("   Region: {} (한국 리전)", region);
        log.info("   Model: {}", modelId);
        log.info("   Model Name: Claude Haiku 4.5");
        log.info("   Max Tokens: {}", maxTokens);
        log.info("   API Key: {}...", apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 10) : "❌ NOT SET");
        log.info("===========================================");

        // API 키 검증
        if (apiKey == null || apiKey.isBlank()) {
            log.error("❌ AWS_BEDROCK_API_KEY가 설정되지 않았습니다!");
            log.error("   .env 파일을 확인해주세요.");
            throw new IllegalStateException("AWS_BEDROCK_API_KEY가 설정되지 않았습니다. .env 파일을 확인해주세요.");
        }

        if (apiKey.length() < 100) {
            log.warn("⚠️ API 키 길이가 {}자로 짧습니다. 올바른 Bedrock API 키인지 확인해주세요.", apiKey.length());
        }

        // 리전 검증
        if (!"ap-northeast-2".equals(region)) {
            log.warn("⚠️ 예상하지 못한 리전입니다. 현재: {}, 예상: ap-northeast-2", region);
        }

        // 모델 ID 검증
        if (!modelId.contains("claude-haiku-4-5")) {
            log.warn("⚠️ 모델 ID가 Claude Haiku 4.5가 아닐 수 있습니다: {}", modelId);
        }

        log.info("✅ Bedrock 설정 검증 완료!");

        return new BedrockProperties(apiKey, region, modelId, maxTokens);
    }

    /**
     * Bedrock 설정 Properties
     */
    @Getter
    public static class BedrockProperties {
        private final String apiKey;
        private final String region;
        private final String modelId;
        private final int maxTokens;

        public BedrockProperties(String apiKey, String region, String modelId, int maxTokens) {
            this.apiKey = apiKey;
            this.region = region;
            this.modelId = modelId;
            this.maxTokens = maxTokens;
        }
    }
    /**
     * AWS Bedrock Runtime Client Bean 설정
     * Titan Embedding 및 Claude 호출 시 SDK가 이 빈을 사용합니다.
     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        // [Efficient Code] 클라이언트를 빌더 패턴으로 생성하여 싱글톤으로 관리
        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                // API Key를 Access Key로 사용하는 환경이라면 아래와 같이 설정 가능합니다.
                // 만약 IAM Role을 사용한다면 credentialsProvider 설정을 생략해도 됩니다.
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(apiKey, "dummy-secret")
                ))
                .build();
    }
}