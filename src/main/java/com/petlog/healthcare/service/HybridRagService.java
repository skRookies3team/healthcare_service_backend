package com.petlog.healthcare.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 하이브리드 RAG 서비스 (검색 알고리즘 개선 버전)
 *
 * 개선 사항:
 * 1. 키워드 동의어 매핑 (눈곱 → 눈물자국)
 * 2. 부분 단어 매칭 (방광 → 방광염)
 * 3. 카테고리 가중치
 * 4. 실시간 크롤링 제거 (로컬 문서만 사용)
 *
 * @author healthcare-team
 * @since 2025-12-31
 */
@Slf4j
//@Service
@RequiredArgsConstructor
public class HybridRagService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    @Value("${pet-health.naver.client-id:}")
    private String naverClientId;

    @Value("${pet-health.naver.client-secret:}")
    private String naverClientSecret;

    @Value("${pet-health.rag.documents-path}")
    private String documentsPath;

    @Value("${pet-health.rag.similarity-threshold:0.3}")
    private double similarityThreshold;

    @Value("${pet-health.rag.top-k:5}")
    private int topK;

    // 라이펫 50개 문서
    private List<HealthDocument> healthDocuments = new ArrayList<>();
    private boolean documentsLoaded = false;

    // 🔥 키워드 동의어 매핑 (검색 개선)
    private static final Map<String, List<String>> KEYWORD_SYNONYMS = Map.ofEntries(
            Map.entry("눈곱", Arrays.asList("눈물자국", "눈물", "눈", "눈꼽")),
            Map.entry("설사", Arrays.asList("묽은변", "물똥", "소화불량", "장염")),
            Map.entry("구토", Arrays.asList("토", "역류", "사료역류")),
            Map.entry("기침", Arrays.asList("켁켁", "헛구역질", "호흡곤란")),
            Map.entry("절뚝", Arrays.asList("다리", "파행", "걸음", "보행이상")),
            Map.entry("혈뇨", Arrays.asList("피오줌", "붉은소변", "방광염")),
            Map.entry("발작", Arrays.asList("경련", "간질", "신경증상")),
            Map.entry("황달", Arrays.asList("노란눈", "간질환", "간")),
            Map.entry("비만", Arrays.asList("살찜", "과체중", "체중증가")),
            Map.entry("털빠짐", Arrays.asList("탈모", "피모", "피부")),
            Map.entry("가려움", Arrays.asList("긁음", "알레르기", "피부염")),
            Map.entry("식욕저하", Arrays.asList("밥안먹음", "입맛없음", "거식")),
            Map.entry("무기력", Arrays.asList("힘없음", "처짐", "기운없음")),
            Map.entry("갈증", Arrays.asList("물많이마심", "다음증", "당뇨"))
    );

    @PostConstruct
    public void loadHealthDocuments() {
        log.info("===========================================");
        log.info("📚 라이펫 건강 문서 로딩 시작");
        log.info("===========================================");
        log.info("   파일 경로: {}", documentsPath);
        log.info("   유사도 임계값: {} (낮춤 - 더 많은 결과)", similarityThreshold);
        log.info("   Top-K: {} (증가)", topK);

        try {
            Resource resource = resourceLoader.getResource(documentsPath);

            if (!resource.exists()) {
                log.error("❌ 문서 파일이 존재하지 않습니다: {}", documentsPath);
                return;
            }

            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode jsonArray = objectMapper.readTree(inputStream);
                log.info("   JSON 파싱 성공: {}개 문서", jsonArray.size());

                int successCount = 0;
                for (JsonNode node : jsonArray) {
                    try {
                        HealthDocument doc = HealthDocument.builder()
                                .id(node.path("id").asText())
                                .title(node.path("title").asText())
                                .content(node.path("content").asText())
                                .category(node.path("category").asText())
                                .keywords(parseKeywords(node.path("keywords")))
                                .url(node.path("url").asText(""))
                                .build();

                        healthDocuments.add(doc);
                        successCount++;

                        if (successCount <= 3) {
                            log.info("      [{}] {} (키워드: {})",
                                    successCount, doc.getTitle(),
                                    String.join(", ", doc.getKeywords()));
                        }

                    } catch (Exception e) {
                        log.warn("   문서 파싱 실패: {}", e.getMessage());
                    }
                }

                documentsLoaded = true;

                log.info("===========================================");
                log.info("✅ 라이펫 문서 로딩 완료: {}개", successCount);
                log.info("   동의어 매핑: {}개 키워드", KEYWORD_SYNONYMS.size());
                log.info("===========================================");
            }

        } catch (IOException e) {
            log.error("❌ 라이펫 문서 로딩 실패", e);
        }
    }

    /**
     * 하이브리드 RAG 검색 (로컬 문서 + 네이버 API)
     */
    public String hybridSearch(String query) {
        log.info("===========================================");
        log.info("🔍 하이브리드 RAG 검색 시작");
        log.info("===========================================");
        log.info("   질문: '{}'", query);

        // 1. 질문 전처리 (동의어 확장)
        String expandedQuery = expandQueryWithSynonyms(query);
        log.info("   확장된 질문: '{}'", expandedQuery);

        try {
            // 2. 로컬 문서 검색
            List<String> localResults = searchLocalDocuments(expandedQuery);
            log.info("   로컬 검색 결과: {}개", localResults.size());

            // 3. 네이버 API (설정된 경우만)
            List<String> naverResults = new ArrayList<>();
            if (!naverClientId.isEmpty()) {
                try {
                    naverResults = searchNaver(query);
                    log.info("   네이버 검색 결과: {}개", naverResults.size());
                } catch (Exception e) {
                    log.debug("   네이버 검색 스킵: {}", e.getMessage());
                }
            }

            // 4. 결과 병합
            List<String> allResults = new ArrayList<>();
            allResults.addAll(localResults);
            allResults.addAll(naverResults);

            // 5. 포맷팅
            String ragContext = formatRagContext(allResults);

            log.info("===========================================");
            log.info("✅ 검색 완료: 총 {}개 결과", allResults.size());
            log.info("   RAG 컨텍스트: {}자", ragContext.length());
            log.info("===========================================");

            return ragContext;

        } catch (Exception e) {
            log.error("❌ RAG 검색 실패", e);
            return "관련 자료를 찾을 수 없습니다. 일반적인 정보를 기반으로 답변합니다.";
        }
    }

    /**
     * 질문 확장 (동의어 추가)
     */
    private String expandQueryWithSynonyms(String query) {
        Set<String> expandedTerms = new HashSet<>(Arrays.asList(query.split("\\s+")));

        // 동의어 추가
        for (String word : query.split("\\s+")) {
            for (Map.Entry<String, List<String>> entry : KEYWORD_SYNONYMS.entrySet()) {
                // 질문에 키워드가 포함되어 있으면
                if (word.contains(entry.getKey()) || entry.getKey().contains(word)) {
                    expandedTerms.addAll(entry.getValue());
                    log.debug("      동의어 추가: '{}' → {}", word, entry.getValue());
                }
            }
        }

        return String.join(" ", expandedTerms);
    }

    /**
     * 로컬 문서 검색 (개선된 알고리즘)
     */
    private List<String> searchLocalDocuments(String query) {
        log.debug("   📄 로컬 문서 검색 중...");

        if (!documentsLoaded || healthDocuments.isEmpty()) {
            log.warn("      ⚠️ 로드된 문서가 없습니다");
            return List.of();
        }

        // 검색어 키워드 추출
        String[] queryKeywords = query.toLowerCase().split("\\s+");
        log.debug("      검색 키워드: {}", Arrays.toString(queryKeywords));

        List<RankedDocument> rankedDocs = healthDocuments.stream()
                .map(doc -> {
                    double score = calculateEnhancedSimilarity(queryKeywords, doc);
                    return new RankedDocument(doc, score);
                })
                .filter(rd -> {
                    boolean pass = rd.getScore() >= similarityThreshold;
                    if (pass) {
                        log.debug("         ✓ {} (점수: {:.2f})",
                                rd.getDocument().getTitle(), rd.getScore());
                    }
                    return pass;
                })
                .sorted(Comparator.comparingDouble(RankedDocument::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());

        List<String> results = rankedDocs.stream()
                .map(rd -> String.format(
                        "[라이펫 문서] %s (관련도: %.0f%%)\n%s",
                        rd.getDocument().getTitle(),
                        rd.getScore() * 100,
                        truncate(rd.getDocument().getContent(), 400)
                ))
                .collect(Collectors.toList());

        log.debug("      → 최종 선택: {}개 문서", results.size());
        return results;
    }

    /**
     * 🔥 개선된 유사도 계산
     *
     * 점수 구성:
     * 1. 제목 키워드 매칭 (40%)
     * 2. 본문 키워드 매칭 (40%)
     * 3. 카테고리 키워드 매칭 (20%)
     */
    private double calculateEnhancedSimilarity(String[] queryKeywords, HealthDocument doc) {
        double titleScore = 0.0;
        double contentScore = 0.0;
        double keywordScore = 0.0;

        String titleLower = doc.getTitle().toLowerCase();
        String contentLower = doc.getContent().toLowerCase();
        List<String> docKeywords = doc.getKeywords();

        int titleMatches = 0;
        int contentMatches = 0;
        int keywordMatches = 0;

        for (String keyword : queryKeywords) {
            if (keyword.length() < 2) continue; // 1글자 제외

            // 1. 제목에서 검색 (완전 일치 또는 부분 일치)
            if (titleLower.contains(keyword)) {
                titleMatches++;
            }

            // 2. 본문에서 검색
            if (contentLower.contains(keyword)) {
                contentMatches++;
            }

            // 3. 문서 키워드에서 검색
            for (String docKeyword : docKeywords) {
                if (docKeyword.toLowerCase().contains(keyword) ||
                        keyword.contains(docKeyword.toLowerCase())) {
                    keywordMatches++;
                    break;
                }
            }
        }

        // 점수 계산 (가중치 적용)
        if (queryKeywords.length > 0) {
            titleScore = (double) titleMatches / queryKeywords.length * 0.4;
            contentScore = (double) contentMatches / queryKeywords.length * 0.4;
            keywordScore = (double) keywordMatches / queryKeywords.length * 0.2;
        }

        double totalScore = titleScore + contentScore + keywordScore;

        // 디버깅 로그
        if (totalScore > 0) {
            log.trace("         [{}] 제목:{:.2f} 본문:{:.2f} 키워드:{:.2f} = {:.2f}",
                    doc.getId(), titleScore, contentScore, keywordScore, totalScore);
        }

        return totalScore;
    }

    /**
     * 네이버 지식백과 검색
     */
    private List<String> searchNaver(String query) {
        log.debug("   📚 네이버 지식백과 검색 중...");

        try {
            String url = "https://openapi.naver.com/v1/search/encyc.json" +
                    "?query=" + query.replace(" ", "+") +
                    "&display=3";

            String response = webClient.get()
                    .uri(url)
                    .header("X-Naver-Client-Id", naverClientId)
                    .header("X-Naver-Client-Secret", naverClientSecret)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            if (response == null) return List.of();

            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");

            if (items.isEmpty()) {
                log.debug("      → 검색 결과 없음");
                return List.of();
            }

            List<String> results = new ArrayList<>();
            for (JsonNode item : items) {
                String title = removeHtmlTags(item.path("title").asText());
                String description = removeHtmlTags(item.path("description").asText());

                results.add(String.format(
                        "[네이버 지식백과] %s\n%s",
                        title, truncate(description, 300)
                ));
            }

            log.debug("      → {}개 발견", results.size());
            return results;

        } catch (Exception e) {
            log.debug("      → 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * RAG 컨텍스트 포맷팅
     */
    private String formatRagContext(List<String> results) {
        if (results.isEmpty()) {
            log.warn("   ⚠️ RAG 검색 결과 없음");
            return "관련 자료를 찾을 수 없습니다. 일반적인 정보를 기반으로 답변합니다.";
        }

        return String.join("\n\n---\n\n", results);
    }

    /**
     * JSON 키워드 배열 파싱
     */
    private List<String> parseKeywords(JsonNode keywordsNode) {
        List<String> keywords = new ArrayList<>();
        if (keywordsNode.isArray()) {
            for (JsonNode keyword : keywordsNode) {
                keywords.add(keyword.asText());
            }
        }
        return keywords;
    }

    /**
     * HTML 태그 제거
     */
    private String removeHtmlTags(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", "").trim();
    }

    /**
     * 텍스트 자르기
     */
    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    /**
     * 건강 문서 DTO
     */
    @lombok.Data
    @lombok.Builder
    public static class HealthDocument {
        private String id;
        private String title;
        private String content;
        private String category;
        @lombok.Builder.Default
        private List<String> keywords = new ArrayList<>();
        private String url;
    }

    /**
     * 랭킹된 문서
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class RankedDocument {
        private HealthDocument document;
        private double score;
    }
}