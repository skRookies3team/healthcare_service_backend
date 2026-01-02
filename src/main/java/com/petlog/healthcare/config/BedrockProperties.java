package com.petlog.healthcare.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AWS Bedrock 설정 (Type-safe)
 *
 * application-local.yml의 placeholder를 .env에서 읽어옴
 * WHY?
 * - IDE 자동완성 지원
 * - 타입 안전성
 * - 테스트시 Mock 쉬움
 */
@Data
@Component
@ConfigurationProperties(prefix = "aws.bedrock")
public class BedrockProperties {

    // AWS Region (ap-northeast-2 = Seoul)
    private String region;

    // AWS Access Key (from .env: AWS_ACCESS_KEY)
    private String accessKey;

    // AWS Secret Key (from .env: AWS_SECRET_KEY)
    private String secretKey;

    // ========== 변경사항: 2가지 모델 ID 추가 ==========

    /**
     * Claude 3.5 Haiku - 빠른 응답 (General Chat)
     * - 비용 저렴
     * - 응답시간 ~100ms
     * - 일반적인 반려동물 관련 질문
     *
     * Default: anthropic.claude-3-5-haiku-20241022-v10
     */
    private String modelId;
    /**
     * Claude 3.5 Sonnet - 강력한 추론 (Persona Chat with RAG)
     * - 더 정확한 응답
     * - 응답시간 ~500ms
     * - RAG 기반 개인화된 조언
     *
     * Default: anthropic.claude-3-5-sonnet-20241022-v20
     */
    private String haikuModelId;

    // Haiku용 토큰 제한 (빠른 응답)
    private Integer haikuMaxTokens = 1000;

    // Sonnet용 토큰 제한 (상세한 응답)
    private Integer sonnetMaxTokens = 2000;

    // 온도 (창의성) - 0.0 ~ 1.0
    private Double temperature = 0.7;
}
