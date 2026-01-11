package com.petlog.healthcare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.petlog.healthcare.entity.VetKnowledge;
import com.petlog.healthcare.repository.VetKnowledgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * 수의사 Q&A 데이터 로더
 * WHY: AI Hub 오픈소스 데이터셋을 파싱하여 DB에 저장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VetQADataLoader {

    private final VetKnowledgeRepository vetKnowledgeRepository;
    private final ObjectMapper objectMapper;

    // 🔧 경로 수정: 언더스코어 → 공백으로 변경
    @Value("${vet.data.base-path:./59.반려견 성장 및 질병 관련 말뭉치 데이터/3.개방데이터/1.데이터}")
    private String basePath;

    /**
     * 모든 Q&A 데이터 로드
     * 
     * @return 로드된 Q&A 수
     */
    @Transactional
    public int loadAllData() {
        log.info("🐕 수의사 Q&A 데이터 로딩 시작...");

        // 이미 데이터가 있으면 스킵
        long existingCount = vetKnowledgeRepository.count();
        if (existingCount > 0) {
            log.info("⏭️ 이미 {}개의 데이터가 존재합니다. 스킵.", existingCount);
            return (int) existingCount;
        }

        AtomicInteger totalLoaded = new AtomicInteger(0);

        // 진료과별 폴더 처리 (외과 추가)
        String[] departments = { "내과", "피부과", "안과", "치과", "외과" };
        for (String dept : departments) {
            int loaded = loadDepartmentData(dept);
            totalLoaded.addAndGet(loaded);
        }

        log.info("🎉 수의사 Q&A 데이터 로딩 완료: 총 {}개", totalLoaded.get());
        return totalLoaded.get();
    }

    /**
     * 특정 진료과 데이터 로드
     */
    @Transactional
    public int loadDepartmentData(String department) {
        // 🔧 폴더명 수정: .zip 접미사 제거 (실제로는 폴더임)
        String folderName = "TL_질의응답데이터_" + department;
        Path dataPath = Paths.get(basePath, "Training", "02.라벨링데이터", folderName);

        if (!Files.exists(dataPath)) {
            log.warn("⚠️ 폴더 없음: {}", dataPath);
            return 0;
        }

        log.info("📂 {} 데이터 로딩: {}", department, dataPath);

        List<VetKnowledge> knowledgeList = new ArrayList<>();
        AtomicInteger count = new AtomicInteger(0);

        try (Stream<Path> files = Files.walk(dataPath, 1)) {
            files.filter(path -> path.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            VetKnowledge knowledge = parseJsonFile(path.toFile(), department);
                            if (knowledge != null) {
                                knowledgeList.add(knowledge);
                                count.incrementAndGet();

                                // 배치 저장 (500개마다)
                                if (knowledgeList.size() >= 500) {
                                    vetKnowledgeRepository.saveAll(knowledgeList);
                                    log.info("💾 {} {}개 저장...", department, count.get());
                                    knowledgeList.clear();
                                }
                            }
                        } catch (Exception e) {
                            log.debug("⚠️ 파일 파싱 실패: {} - {}", path, e.getMessage());
                        }
                    });

            // 나머지 저장
            if (!knowledgeList.isEmpty()) {
                vetKnowledgeRepository.saveAll(knowledgeList);
            }

        } catch (IOException e) {
            log.error("❌ 폴더 읽기 실패: {}", dataPath, e);
            return 0;
        }

        log.info("✅ {} 완료: {}개", department, count.get());
        return count.get();
    }

    /**
     * JSON 파일 파싱
     */
    private VetKnowledge parseJsonFile(File file, String department) throws IOException {
        JsonNode root = objectMapper.readTree(file);

        // meta 정보
        JsonNode meta = root.path("meta");
        String lifeCycle = meta.path("lifeCycle").asText(null);
        String disease = meta.path("disease").asText(null);

        // Q&A 정보
        JsonNode qa = root.path("qa");
        String instruction = qa.path("instruction").asText(null);
        String question = qa.path("input").asText(null);
        String answer = qa.path("output").asText(null);

        // 필수 필드 검증
        if (question == null || question.isBlank() || answer == null || answer.isBlank()) {
            return null;
        }

        return VetKnowledge.builder()
                .department(department)
                .disease(disease)
                .lifeCycle(lifeCycle)
                .instruction(instruction)
                .question(question)
                .answer(answer)
                .sourceFile(file.getName())
                .build();
    }

    /**
     * 데이터 통계 조회
     */
    public String getDataStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 수의사 지식 베이스 통계\n");
        sb.append("========================\n");

        List<Object[]> stats = vetKnowledgeRepository.countByDepartment();
        long total = 0;

        for (Object[] row : stats) {
            String dept = (String) row[0];
            Long count = (Long) row[1];
            sb.append(String.format("- %s: %,d개\n", dept, count));
            total += count;
        }

        sb.append("------------------------\n");
        sb.append(String.format("총계: %,d개\n", total));

        return sb.toString();
    }

    /**
     * 전체 데이터 삭제 (재로딩용)
     */
    @Transactional
    public void clearAllData() {
        log.warn("⚠️ 모든 수의사 Q&A 데이터 삭제...");
        vetKnowledgeRepository.deleteAll();
        log.info("✅ 데이터 삭제 완료");
    }
}
