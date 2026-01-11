package com.petlog.healthcare.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security Configuration for Healthcare Service
 *
 * Gateway에서 JWT 검증 후 X-User-Id 헤더로 사용자 정보 전달
 * Healthcare Service는 해당 헤더를 신뢰하고 처리
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        /**
         * Security Filter Chain
         * - 모든 요청 허용 (Gateway에서 JWT 검증)
         * - CORS 설정
         * - Stateless 세션
         */
        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                // CSRF 비활성화 (REST API)
                                .csrf(csrf -> csrf.disable())

                                // ⚠️ CORS 비활성화 - API Gateway에서 처리함 (중복 헤더 방지)
                                .cors(cors -> cors.disable())

                                // 세션 관리 (Stateless)
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // 요청 권한 설정
                                .authorizeHttpRequests(auth -> auth
                                                // Public endpoints
                                                .requestMatchers("/api/health/**").permitAll()
                                                .requestMatchers("/api/test/**").permitAll()
                                                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                                                .requestMatchers("/actuator/**").permitAll()
                                                // All other requests - Gateway handles JWT
                                                .anyRequest().permitAll());

                return http.build();
        }

        /**
         * CORS Configuration
         * CloudFront, localhost 허용
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();

                // 허용 Origin (개발용: 모든 origin 허용)
                configuration.setAllowedOriginPatterns(Arrays.asList("*")); // ⭐ 모든 origin 허용

                // 허용 메서드
                configuration.setAllowedMethods(Arrays.asList(
                                "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));

                // 허용 헤더
                configuration.setAllowedHeaders(List.of("*"));

                // 노출 헤더 (Gateway에서 전달하는 사용자 정보)
                configuration.setExposedHeaders(Arrays.asList(
                                "X-User-Id",
                                "X-User-Email",
                                "X-Total-Count"));

                // 자격 증명 허용
                configuration.setAllowCredentials(true);

                // Preflight 캐시 시간
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);

                return source;
        }
}
