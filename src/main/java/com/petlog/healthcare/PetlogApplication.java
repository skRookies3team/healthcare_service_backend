package com.petlog.healthcare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Healthcare AI Chatbot Service (포트: 8085)
 * MSA PetLog 프로젝트 핵심 서비스
 *
 *  수정사항:
 * - @EnableJpaRepositories 추가하여 명시적으로 repository 스캔
 * - ChatHistoryRepository 빈 등록 문제 해결
 *
 * @author healthcare-team
 * @since 2026-01-02
 * @version 2.0
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.petlog.healthcare.domain.repository")
@EnableKafka
public class PetlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetlogApplication.class, args);
    }
}