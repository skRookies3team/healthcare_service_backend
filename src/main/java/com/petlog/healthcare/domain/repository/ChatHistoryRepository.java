// src/main/java/com/petlog/healthcare/domain/repository/ChatHistoryRepository.java
package com.petlog.healthcare.domain.repository;

import com.petlog.healthcare.entity.ChatHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chat History Repository
 *
 * 대화 이력 조회 및 관리
 */
@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistory, Long> {

    /**
     * 사용자-펫의 최근 대화 이력 조회 (최신 N개)
     *
     * @param userId 사용자 ID
     * @param petId 펫 ID
     * @param limit 조회 개수
     * @return 대화 이력 목록
     */
    @Query(value = """
        SELECT ch FROM ChatHistory ch 
        WHERE ch.userId = :userId AND ch.petId = :petId 
        ORDER BY ch.createdAt DESC 
        LIMIT :limit
        """)
    List<ChatHistory> findRecentChats(
            @Param("userId") Long userId,
            @Param("petId") Long petId,
            @Param("limit") int limit
    );

    /**
     * 특정 기간의 대화 이력 조회
     */
    List<ChatHistory> findByUserIdAndPetIdAndCreatedAtBetween(
            Long userId,
            Long petId,
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * 채팅 타입별 조회
     */
    List<ChatHistory> findByUserIdAndPetIdAndChatType(
            Long userId,
            Long petId,
            String chatType
    );
}