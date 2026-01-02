package com.petlog.healthcare.infrastructure.kafka;

import com.petlog.healthcare.dto.event.DiaryEventMessage;
import com.petlog.healthcare.service.DiaryVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Kafka Consumer: Diary 이벤트 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiaryEventConsumer {

    private final DiaryVectorService diaryVectorService;

    @KafkaListener(topics = "diary-events", groupId = "healthcare-group")
    public void consume(@Payload DiaryEventMessage event,
                        @Header(KafkaHeaders.RECEIVED_PARTITION) int partition, // [해결] 상수를 정확히 수정
                        @Header(KafkaHeaders.OFFSET) long offset,
                        Acknowledgment ack) {

        log.info("📩 Kafka 메시지 수신: diaryId={}, partition={}, offset={}",
                event.getDiaryId(), partition, offset);

        try {
            switch (event.getEventType()) {
                case "DIARY_CREATED" -> {
                    // [해결] DiaryVectorService의 파라미터 순서에 맞춰 전달
                    diaryVectorService.vectorizeAndStore(
                            event.getDiaryId(), event.getUserId(), event.getPetId(),
                            event.getContent(), event.getImageUrl(), event.getCreatedAt()
                    );
                }
                case "DIARY_UPDATED" -> {
                    // [해결] updateVector가 없으므로 삭제 후 다시 저장 (가장 확실한 방법)
                    diaryVectorService.deleteVector(event.getDiaryId());
                    diaryVectorService.vectorizeAndStore(
                            event.getDiaryId(), event.getUserId(), event.getPetId(),
                            event.getContent(), event.getImageUrl(), event.getCreatedAt()
                    );
                }
                case "DIARY_DELETED" -> {
                    diaryVectorService.deleteVector(event.getDiaryId());
                }
                default -> log.warn("⚠️ 알 수 없는 이벤트: {}", event.getEventType());
            }

            // 수동 커밋 모드인 경우 반드시 호출해야 합니다.
            if (ack != null) {
                ack.acknowledge();
            }
            log.info("✅ 처리 완료: diaryId={}", event.getDiaryId());

        } catch (Exception e) {
            log.error("❌ 처리 실패: diaryId={}, error={}", event.getDiaryId(), e.getMessage());
        }
    }
}