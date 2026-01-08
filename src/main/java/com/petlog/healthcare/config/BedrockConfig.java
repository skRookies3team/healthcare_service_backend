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
 * AWS Bedrock Dual Models (Sonnet + Haiku) 최적화 설정
 *
 * 기본: Sonnet (anthropic.claude-3-5-sonnet-20240620-v1:0)
 * 빠른: Haiku (anthropic.claude-haiku-4-5-20251001-v1:0)
 * 리전: ap-northeast-2 (한국)
 * 인증: Long-term API Key + StaticCredentials
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@Slf4j
@Configuration
public class BedrockConfig {

    @Value("${AWS_BEDROCK_API_KEY}")
    private String apiKey;

    @Value("${AWS_BEDROCK_REGION}")
    private String region;

    @Value("${AWS_BEDROCK_MODEL_ID}")
    private String modelId;  // Sonnet (기본)

    @Value("${AWS_BEDROCK_HAIKU_MODEL_ID}")
    private String haikuModelId;  // Haiku

    @Value("${AWS_BEDROCK_MAX_TOKENS:2000}")
    private int maxTokens;

    /**
     * 🚀 Bedrock 설정 검증 + Properties 반환
     * 당신의 기존 로직 완전 유지
     */
    @Bean
    public BedrockProperties bedrockProperties() {
        log.info("===========================================");
        log.info(" 🔥 Bedrock Dual Models 설정 완료");
        log.info("===========================================");
        log.info("   Region: {} (한국 리전)", region);
        log.info("   🧠 Sonnet: {}", modelId);
        log.info("   ⚡ Haiku: {}", haikuModelId);
        log.info("   Max Tokens: {}", maxTokens);
        log.info("   API Key: {}...", apiKey != null && apiKey.length() > 10
                ? apiKey.substring(0, 10) : "❌ NOT SET");
        log.info("===========================================");

        // 🔍 기존 검증 로직 완전 유지
        validateApiKey();
        validateRegion();
        validateModels();

        log.info("✅ Bedrock Dual Models 검증 완료!");
        return new BedrockProperties(apiKey, region, modelId, haikuModelId, maxTokens);
    }

    /**
     * 🛡️ 당신의 기존 API 키 검증 로직 - 완전 복사
     */
    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("❌ AWS_BEDROCK_API_KEY가 설정되지 않았습니다!");
            log.error("   .env 파일을 확인해주세요.");
            throw new IllegalStateException("AWS_BEDROCK_API_KEY가 설정되지 않았습니다. .env 파일을 확인해주세요.");
        }
        if (apiKey.length() < 100) {
            log.warn("⚠️ API 키 길이가 {}자로 짧습니다. 올바른 Bedrock API 키인지 확인해주세요.", apiKey.length());
        }
    }

    /**
     * 🗺️ 당신의 기존 리전 검증 - 완전 복사
     */
    private void validateRegion() {
        if (!"ap-northeast-2".equals(region)) {
            log.warn("⚠️ 예상하지 못한 리전입니다. 현재: {}, 예상: ap-northeast-2", region);
        }
    }

    /**
     * 🎯 Dual 모델 검증 - 신규 추가
     */
    private void validateModels() {
        if (!modelId.contains("sonnet")) {
            log.warn("⚠️ Sonnet 모델 ID 확인: {}", modelId);
        }
        if (!haikuModelId.contains("haiku")) {
            log.warn("⚠️ Haiku 모델 ID 확인: {}", haikuModelId);
        }
    }

    /**
     * BedrockProperties 내부 클래스
     * 당신의 기존 구조 유지 + Haiku 추가
     */
    @Getter
    public static class BedrockProperties {
        private final String apiKey;
        private final String region;
        private final String modelId;      // Sonnet
        private final String haikuModelId; // Haiku
        private final int maxTokens;

        public BedrockProperties(String apiKey, String region, String modelId,
                                 String haikuModelId, int maxTokens) {
            this.apiKey = apiKey;
            this.region = region;
            this.modelId = modelId;
            this.haikuModelId = haikuModelId;
            this.maxTokens = maxTokens;
        }
    }

    /**
     * 🌟 BedrockRuntimeClient - 당신의 기존 코드 완전 복사 + Dual Models 지원
     * StaticCredentialsProvider로 API 키 처리
     * 싱글톤 Bean으로 효율적 관리
     */
    @Bean
    public BedrockRuntimeClient bedrockRuntimeClient() {
        log.info("🏭 BedrockRuntimeClient 생성 중...");

        return BedrockRuntimeClient.builder()
                .region(Region.of(region))
                // 🔑 Long-term API Key → StaticCredentials 변환 (당신의 기존 방식)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                apiKey,  // API Key as Access Key
                                "bedrock-long-term-secret"  // Dummy Secret (Bedrock API Key 방식)
                        )
                ))
                .build();
    }
}