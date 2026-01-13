package com.petlog.healthcare.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Diary Event Message (Kafka)
 *
 * Diary Service로부터 받는 이벤트
 *
 * @author healthcare-team
 * @since 2025-12-23
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaryEventMessage {

    private String eventType;
    private Long diaryId;
    private String userId; // UUID 형식 지원
    private Long petId;
    private String content;
    private String imageUrl;
    private LocalDateTime createdAt;
}
