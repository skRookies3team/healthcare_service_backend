package com.petlog.healthcare.config;

import com.petlog.healthcare.dto.event.DiaryEventMessage;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer 설정
 *
 * Diary Service(8087)로부터 이벤트 수신
 * WHY: kafka.enabled=true일 때만 활성화 (Docker Kafka 없을 때 에러 방지)
 *
 * @author healthcare-team
 * @since 2026-01-02
 */
@EnableKafka
@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = false)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, DiaryEventMessage> consumerFactory() {
        Map<String, Object> config = new HashMap<>();

        // Kafka 서버 주소
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Consumer Group ID
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);

        // Deserializer 설정
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // ✅ JSON 역직렬화 신뢰 패키지 설정 (중요!)
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        // ✅ 타입 매핑 (Diary Service의 패키지 경로와 일치시킴)
        config.put(JsonDeserializer.TYPE_MAPPINGS,
                "diaryEvent:com.petlog.healthcare.dto.event.DiaryEventMessage");

        // Offset 관리
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // 수동 커밋

        // 성능 최적화
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        config.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1);

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                new JsonDeserializer<>(DiaryEventMessage.class, false));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, DiaryEventMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, DiaryEventMessage> factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());

        // ✅ 수동 커밋 모드 활성화
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);

        // 동시 처리 스레드 수 (파티션 3개이므로 3개 설정)
        factory.setConcurrency(3);

        return factory;
    }
}