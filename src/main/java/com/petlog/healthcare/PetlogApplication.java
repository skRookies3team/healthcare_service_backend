package com.petlog.healthcare; //
import com.petlog.healthcare.config.BedrockProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients; // Feign 사용 시 필요
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableFeignClients // 나중에 다른 서비스 호출할 때 필요
@EnableConfigurationProperties(BedrockProperties.class)

public class HealthcareApplication {

    public static void main(String[] args) {
        SpringApplication.run(HealthcareApplication.class, args);
    }
}