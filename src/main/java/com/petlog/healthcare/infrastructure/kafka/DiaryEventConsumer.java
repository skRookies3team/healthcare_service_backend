package com.petlog.healthcare.infrastructure.kafka;

import com.petlog.healthcare.dto.event.DiaryEventMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Diary Event Kafka Consumer
 *
 * Diary Service로부터 일기 생성/수정/삭제 이벤트를 수신하여 처리
 *
 * WHY Consumer Pattern?
 * - Event-Driven Architecture (EDA) 구현
 * - Diary Service와 Healthcare Service 간 느슨한 결합
 * - 비동기 처리로 응답 시간 단축
 *
 * WHY @Component?
 * - Spring Bean으로 등록하여 자동 실행
 * - @KafkaListener가 활성화됨
 *
 * 처리 흐름:
 * 1. Kafka에서 메시지 수신
 * 2. DiaryEventMessage로 역직렬화
 * 3. eventType에 따라 분기 처리
 * 4. 벡터화 Service Layer 호출 (TODO)
 *
 * @author healthcare-team
 * @since 2025-12-24
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryEventConsumer {

    // TODO: 벡터화 Service 주입 (Phase 2)
    // private final DiaryVectorService diaryVectorService;

    /**
     * Kafka Topic: diary-events 메시지 수신
     *
     * WHY @KafkaListener?
     * - Spring Kafka가 자동으로 메시지 폴링
     * - 멀티스레드 처리 지원
     * - 에러 핸들링 자동화
     *
     * WHY topics="diary-events"?
     * - Diary Service의 Producer와 일치
     *
     * WHY groupId="healthcare-group"?
     * - application.yaml의 group-id와 일치
     * - Consumer Group으로 메시지 중복 처리 방지
     *
     * WHY @Payload?
     * - Kafka 메시지 본문을 DiaryEventMessage로 역직렬화
     *
     * WHY @Header?
     * - Kafka 메타데이터 (파티션, 오프셋 등) 추출
     * - 로깅 및 디버깅에 활용
     *
     * @param message Diary 이벤트 메시지
     * @param partition Kafka 파티션 번호
     * @param offset Kafka 오프셋 (메시지 위치)
     */
    @KafkaListener(
            topics = "diary-events",
            groupId = "healthcare-group"
    )
    public void consumeDiaryEvent(
            @Payload DiaryEventMessage message,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("========================================");
        log.info("📩 Received Diary Event from Kafka");
        log.info("========================================");
        log.info("Event Type: {}", message.getEventType());
        log.info("Diary ID: {}", message.getDiaryId());
        log.info("User ID: {}", message.getUserId());
        log.info("Pet ID: {}", message.getPetId());
        log.info("Content: {}", truncateContent(message.getContent(), 100));
        log.info("Image URL: {}", message.getImageUrl());
        log.info("Created At: {}", message.getCreatedAt());
        log.info("Partition: {}, Offset: {}", partition, offset);
        log.info("========================================");

        try {
            // ========================================
            // Event Type별 처리 로직
            // WHY switch?
            // - 명확한 분기 처리
            // - 유지보수 용이
            // ========================================
            switch (message.getEventType()) {
                case "DIARY_CREATED":
                    handleDiaryCreated(message);
                    break;

                case "DIARY_UPDATED":
                    handleDiaryUpdated(message);
                    break;

                case "DIARY_DELETED":
                    handleDiaryDeleted(message);
                    break;

                default:
                    log.warn("⚠️ Unknown event type: {}", message.getEventType());
            }

        } catch (Exception e) {
            // ========================================
            // 에러 처리
            // WHY 예외를 던지지 않는가?
            // - Kafka Consumer가 멈추는 것을 방지
            // - 하나의 메시지 실패가 전체에 영향 없도록
            //
            // TODO: 실패 메시지 Dead Letter Queue로 전송 (Phase 2)
            // ========================================
            log.error("❌ Failed to process diary event - diaryId: {}, eventType: {}",
                    message.getDiaryId(), message.getEventType(), e);
        }
    }

    /**
     * DIARY_CREATED 이벤트 처리
     *
     * WHY 별도 메서드?
     * - 단일 책임 원칙 (SRP)
     * - 테스트 용이
     * - 코드 가독성
     *
     * 처리 로직:
     * 1. 일기 내용 벡터화 (OpenAI Embeddings)
     * 2. 벡터 DB에 저장 (Milvus/PostgreSQL pgvector)
     * 3. RAG 시스템에서 활용 가능
     *
     * @param message Diary 생성 이벤트
     */
    private void handleDiaryCreated(DiaryEventMessage message) {
        log.info("✅ Processing DIARY_CREATED event");
        log.info("   → TODO: OpenAI Embeddings 벡터화");
        log.info("   → TODO: Vector DB 저장");
        log.info("   → TODO: RAG 시스템 업데이트");

        // TODO: Phase 2 구현
        // diaryVectorService.createVector(message);
    }

    /**
     * DIARY_UPDATED 이벤트 처리
     *
     * 처리 로직:
     * 1. 기존 벡터 삭제
     * 2. 새로운 내용 벡터화
     * 3. 벡터 DB 업데이트
     *
     * @param message Diary 수정 이벤트
     */
    private void handleDiaryUpdated(DiaryEventMessage message) {
        log.info("✅ Processing DIARY_UPDATED event");
        log.info("   → TODO: 기존 벡터 삭제");
        log.info("   → TODO: 새로운 벡터 생성");
        log.info("   → TODO: Vector DB 업데이트");

        // TODO: Phase 2 구현
        // diaryVectorService.updateVector(message);
    }

    /**
     * DIARY_DELETED 이벤트 처리
     *
     * 처리 로직:
     * 1. 벡터 DB에서 해당 일기 벡터 삭제
     * 2. 관련 메타데이터 삭제
     *
     * @param message Diary 삭제 이벤트
     */
    private void handleDiaryDeleted(DiaryEventMessage message) {
        log.info("✅ Processing DIARY_DELETED event");
        log.info("   → TODO: Vector DB에서 벡터 삭제");
        log.info("   → TODO: 메타데이터 삭제");

        // TODO: Phase 2 구현
        // diaryVectorService.deleteVector(message.getDiaryId());
    }

    /**
     * 로그 출력용 Content 자르기
     *
     * WHY 필요?
     * - 긴 일기 내용이 로그를 어지럽히는 것 방지
     * - 디버깅 시 가독성 향상
     *
     * @param content 원본 내용
     * @param maxLength 최대 길이
     * @return 잘린 내용
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "null";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }
}
