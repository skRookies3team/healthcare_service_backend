package com.petlog.healthcare.infrastructure.kafka;

import com.petlog.healthcare.dto.event.DiaryEventMessage;
import com.petlog.healthcare.service.DiaryVectorService;
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
 * Record Service로부터 일기 생성/수정/삭제 이벤트를 수신하여 벡터 DB 처리
 *
 * [핵심 기능]
 * 1. Kafka Topic 'diary-events'에서 메시지 수신
 * 2. 이벤트 타입별 분기 처리 (CREATED/UPDATED/DELETED)
 * 3. DiaryVectorService를 통해 Milvus 벡터 DB에 저장/수정/삭제
 *
 * [아키텍처 결정]
 * - WHY Event-Driven?
 *   → Record Service와 Healthcare Service 간 느슨한 결합
 *   → 비동기 처리로 Record Service 응답 시간 단축
 *   → 장애 격리 (벡터화 실패해도 일기 저장은 성공)
 *
 * - WHY @Component?
 *   → Spring Bean으로 등록하여 @KafkaListener 자동 활성화
 *   → 애플리케이션 시작 시 자동으로 Consumer 실행
 *
 * @author healthcare-team
 * @since 2025-12-24
 * @version 2.0 (Kafka Consumer 활성화 - 2025-01-02)
 *
 * Issue: #healthcare-kafka-consumer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryEventConsumer {

    private final DiaryVectorService diaryVectorService;

    /**
     * Kafka Topic: diary-events 메시지 수신
     *
     * [설정 상세]
     * - topics: "diary-events" (Record Service의 Producer와 일치)
     * - groupId: "healthcare-group" (application.yaml 설정과 일치)
     * - containerFactory: Spring Kafka 기본 설정 사용
     *
     * [Consumer Group 전략]
     * - 같은 groupId를 가진 Consumer는 파티션을 분산하여 처리
     * - 메시지 중복 처리 방지
     * - Scale-out 가능 (Healthcare 서비스 여러 대 실행 시 자동 분산)
     *
     * [에러 핸들링]
     * - try-catch로 예외 처리하여 하나의 메시지 실패가 전체에 영향 없도록 함
     * - 실패 메시지는 로그로만 남김 (향후 Dead Letter Queue 구현 예정)
     *
     * @param message Diary 이벤트 메시지 (JSON → DiaryEventMessage 자동 역직렬화)
     * @param partition Kafka 파티션 번호 (로깅용)
     * @param offset Kafka 오프셋 (메시지 위치, 로깅용)
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
        log.info("📩 Kafka 이벤트 수신");
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
            // 이벤트 타입별 처리 로직
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
                    log.warn("⚠️ 알 수 없는 이벤트 타입: {}", message.getEventType());
            }

        } catch (Exception e) {
            // ========================================
            // 에러 처리 (Consumer 중단 방지)
            // ========================================
            log.error("❌ 이벤트 처리 실패 - diaryId: {}, eventType: {}",
                    message.getDiaryId(), message.getEventType(), e);

            // TODO: Dead Letter Queue로 실패 메시지 전송 (Phase 2)
        }
    }

    /**
     * DIARY_CREATED 이벤트 처리
     *
     * [처리 로직]
     * 1. DiaryVectorService.vectorizeAndStore() 호출
     * 2. 일기 내용을 OpenAI Embeddings로 벡터화
     * 3. Milvus Vector DB에 저장
     * 4. RAG 시스템에서 활용 가능하게 됨
     *
     * [WHY 벡터화?]
     * - AI 페르소나 챗봇이 과거 일기를 참조하여 답변하기 위함
     * - 유사도 검색(Similarity Search)으로 관련 일기 빠르게 찾기
     *
     * @param message Diary 생성 이벤트
     */
    private void handleDiaryCreated(DiaryEventMessage message) {
        log.info("✅ DIARY_CREATED 이벤트 처리 시작");
        log.info("   → DiaryVectorService 호출");

        diaryVectorService.vectorizeAndStore(
                message.getDiaryId(),
                message.getUserId(),
                message.getPetId(),
                message.getContent(),
                message.getImageUrl(),
                message.getCreatedAt()
        );

        log.info("✅ 벡터화 완료 - Milvus에 저장됨");
    }

    /**
     * DIARY_UPDATED 이벤트 처리
     *
     * [처리 로직]
     * 1. 기존 벡터 삭제 (DiaryVectorService.deleteVector)
     * 2. 새로운 내용 벡터화 (DiaryVectorService.vectorizeAndStore)
     * 3. Milvus Vector DB 업데이트
     *
     * [WHY 삭제 후 재생성?]
     * - Milvus는 벡터 업데이트를 직접 지원하지 않음
     * - 삭제 → 재생성이 가장 안전한 방법
     *
     * @param message Diary 수정 이벤트
     */
    private void handleDiaryUpdated(DiaryEventMessage message) {
        log.info("✅ DIARY_UPDATED 이벤트 처리 시작");
        log.info("   → 기존 벡터 삭제");

        // Step 1: 기존 벡터 삭제
        diaryVectorService.deleteVector(message.getDiaryId());

        log.info("   → 새로운 벡터 생성");

        // Step 2: 새로운 벡터 생성
        diaryVectorService.vectorizeAndStore(
                message.getDiaryId(),
                message.getUserId(),
                message.getPetId(),
                message.getContent(),
                message.getImageUrl(),
                message.getCreatedAt()
        );

        log.info("✅ 벡터 업데이트 완료");
    }

    /**
     * DIARY_DELETED 이벤트 처리
     *
     * [처리 로직]
     * 1. Milvus Vector DB에서 해당 일기 벡터 삭제
     * 2. 관련 메타데이터 삭제
     *
     * [WHY 삭제?]
     * - GDPR 등 개인정보 보호 규정 준수
     * - RAG 시스템에서 삭제된 일기는 참조되지 않아야 함
     *
     * @param message Diary 삭제 이벤트
     */
    private void handleDiaryDeleted(DiaryEventMessage message) {
        log.info("✅ DIARY_DELETED 이벤트 처리 시작");
        log.info("   → Milvus Vector DB에서 삭제");

        diaryVectorService.deleteVector(message.getDiaryId());

        log.info("✅ 벡터 삭제 완료");
    }

    /**
     * 로그 출력용 Content 자르기
     *
     * [WHY 필요?]
     * - 긴 일기 내용이 로그를 어지럽히는 것 방지
     * - 디버깅 시 가독성 향상
     *
     * @param content 원본 내용
     * @param maxLength 최대 길이
     * @return 잘린 내용 (원본이 짧으면 그대로 반환)
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