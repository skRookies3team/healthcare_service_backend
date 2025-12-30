package com.petlog.healthcare; //
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients; // Feign 사용 시 필요
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Healthcare AI Chatbot Service 메인 애플리케이션
 *
 * WHY SpringBootApplication?
 * - Auto Configuration (JPA, Kafka, WebFlux 자동 설정)
 * - Component Scan (DDD 패키지 구조 자동 탐지)
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.petlog.healthcare")
@EnableKafka
public class PetlogApplication {
    public static void main(String[] args) {
        SpringApplication.run(PetlogApplication.class, args);
    }
}
