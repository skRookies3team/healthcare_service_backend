package com.petlog.healthcare.service;

import com.petlog.healthcare.dto.hospital.HospitalResponse;
import com.petlog.healthcare.dto.hospital.HospitalResponse.HospitalInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 동물병원 검색 서비스
 *
 * CSV 파일에서 로드한 병원 데이터 검색
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HospitalService {

    private final HospitalDataLoader hospitalDataLoader;

    /**
     * 주변 동물병원 검색 (위치 기반 - 현재는 전체 반환)
     * 주변 동물병원 검색 (위치 + 전문진료과 필터)
     *
     * @param latitude  위도
     * @param longitude 경도
     * @param radiusKm  반경 (km)
     * @param specialty 전문 진료과 (선택 사항)
     * @return 병원 목록
     */
    public HospitalResponse findNearbyHospitals(double latitude, double longitude, int radiusKm, String specialty) {
        log.info("🏥 주변 동물병원 검색 (전문과목: {})", specialty != null ? specialty : "전체");
        log.info("   위치: ({}, {}), 반경: {}km", latitude, longitude, radiusKm);

        try {
            // 전체 데이터에서 거리 계산 후 필터링 및 정렬
            List<HospitalInfo> hospitals = hospitalDataLoader.getAllHospitals().stream()
                    .filter(h -> h.getLatitude() != null && h.getLongitude() != null)
                    // 전문 진료과 필터링 (specialty가 있으면 해당 단어가 포함된 병원만)
                    .filter(h -> specialty == null
                            || (h.getSpecialty() != null && h.getSpecialty().contains(specialty)))
                    .map(h -> {
                        // 거리 계산 (Haversine Formula)
                        double dist = calculateDistance(latitude, longitude, h.getLatitude(), h.getLongitude());

                        // HospitalInfo 재구성
                        return HospitalInfo.builder()
                                .name(h.getName())
                                .address(h.getAddress())
                                .roadAddress(h.getRoadAddress())
                                .phone(h.getPhone())
                                .latitude(h.getLatitude())
                                .longitude(h.getLongitude())
                                .distance(Math.round(dist * 100) / 100.0) // 소수점 2자리
                                .operatingHours(h.getOperatingHours())
                                .isEmergency(h.isEmergency())
                                .specialty(h.getSpecialty())
                                .build();
                    })
                    .filter(h -> h.getDistance() <= radiusKm) // 반경 내 필터링
                    .sorted((h1, h2) -> Double.compare(h1.getDistance(), h2.getDistance())) // 가까운 순 정렬
                    .limit(5) // 상위 5개만
                    .toList();

            log.info("✅ 검색 결과: {}개 병원 (최단 거리: {}km)", hospitals.size(),
                    hospitals.isEmpty() ? "none" : hospitals.get(0).getDistance());
            return HospitalResponse.success(hospitals);

        } catch (Exception e) {
            log.error("❌ 병원 검색 실패: {}", e.getMessage(), e);
            return HospitalResponse.error("검색 중 오류가 발생했습니다: " + e.getMessage());
        }
    }

    /**
     * 주변 동물병원 검색 (기본)
     *
     * @param latitude  위도
     * @param longitude 경도
     * @param radiusKm  반경 (km)
     * @return 병원 목록
     */
    public HospitalResponse findNearbyHospitals(double latitude, double longitude, int radiusKm) {
        return findNearbyHospitals(latitude, longitude, radiusKm, null);
    }

    /**
     * 지역명으로 동물병원 검색
     *
     * @param region 지역명 (예: 서울, 강남구)
     * @return 병원 목록
     */
    public HospitalResponse findByRegion(String region) {
        log.info("🏥 지역별 동물병원 검색 - region: {}", region);

        try {
            List<HospitalInfo> hospitals = hospitalDataLoader.findByRegion(region);

            log.info("✅ 검색 결과: {}개 병원 (지역: {})", hospitals.size(), region);
            return HospitalResponse.success(hospitals);

        } catch (Exception e) {
            log.error("❌ 병원 검색 실패: {}", e.getMessage(), e);
            return HospitalResponse.error("검색 중 오류가 발생했습니다.");
        }
    }

    /**
     * 병원명으로 검색
     *
     * @param keyword 검색어
     * @return 병원 목록
     */
    public HospitalResponse searchByName(String keyword) {
        log.info("🔍 병원명 검색 - keyword: {}", keyword);

        try {
            List<HospitalInfo> hospitals = hospitalDataLoader.searchByName(keyword);

            log.info("✅ 검색 결과: {}개 병원", hospitals.size());
            return HospitalResponse.success(hospitals);

        } catch (Exception e) {
            log.error("❌ 검색 실패: {}", e.getMessage(), e);
            return HospitalResponse.error("검색 중 오류가 발생했습니다.");
        }
    }

    /**
     * 24시/응급 병원 검색
     *
     * @return 응급 병원 목록
     */
    public HospitalResponse findEmergencyHospitals() {
        log.info("🚨 응급 동물병원 검색");

        try {
            List<HospitalInfo> hospitals = hospitalDataLoader.findEmergencyHospitals();

            log.info("✅ 응급 병원: {}개", hospitals.size());
            return HospitalResponse.success(hospitals);

        } catch (Exception e) {
            log.error("❌ 검색 실패: {}", e.getMessage(), e);
            return HospitalResponse.error("검색 중 오류가 발생했습니다.");
        }
    }

    /**
     * 전체 병원 수 조회
     */
    public int getTotalCount() {
        return hospitalDataLoader.getTotalCount();
    }

    /**
     * 두 좌표 간의 거리 계산 (Haversine Formula)
     * 
     * @return 거리 (km)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371; // 지구 반지름 (km)

        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c;
    }
}
