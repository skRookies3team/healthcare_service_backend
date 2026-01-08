package com.petlog.healthcare.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Repository 스캔 설정
 *
 * ✅ 이 파일과 PetlogApplication.java 중 하나만 있어도 됩니다.
 * ✅ 둘 다 있으면 중복이지만 문제는 없습니다.
 *
 * WHY?
 * - ChatHistoryRepository가 스캔되지 않아서 빈 등록 안 됨
 * - 명시적으로 repository 패키지 지정
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {
                "com.petlog.healthcare.domain.repository",
                "com.petlog.healthcare.repository"  // 혹시 다른 경로에도 있다면
        }
)
public class JpaConfig {
    // 이 설정으로 com.petlog.healthcare.domain.repository 하위의
    // 모든 JpaRepository가 Spring Bean으로 등록됩니다.
}