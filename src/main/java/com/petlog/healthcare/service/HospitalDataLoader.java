package com.petlog.healthcare.service;

import com.petlog.healthcare.dto.hospital.HospitalResponse.HospitalInfo;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 동물병원 CSV 데이터 로더
 *
 * 공공데이터포털에서 받은 CSV 파일을 로드하여 메모리에 캐싱
 * 위치 기반 거리 계산 지원
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@Service
public class HospitalDataLoader {

    private final List<HospitalInfo> allHospitals = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadHospitalData();
        loadSampleData(); // CSV 로드 여부와 관계없이 필수 데이터(Preset) 로드 보장
    }

    /**
     * CSV 파일에서 병원 데이터 로드
     */
    private void loadHospitalData() {
        log.info("═══════════════════════════════════════");
        log.info("🏥 동물병원 CSV 데이터 로딩 시작");

        try {
            // 여러 경로 시도
            Resource resource = null;
            String[] paths = {
                    "data/hospital_data.csv",
                    "동물병원_DATA.csv",
                    "hospital_data.csv"
            };

            for (String path : paths) {
                Resource r = new ClassPathResource(path);
                if (r.exists()) {
                    resource = r;
                    log.info("✅ CSV 파일 발견: {}", path);
                    break;
                }
            }

            if (resource == null) {
                log.warn("⚠️ CSV 파일 없음 - 샘플 데이터만 사용됩니다.");
                return;
            }

            // EUC-KR 또는 UTF-8 시도
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), Charset.forName("EUC-KR")))) {
                parseCSV(reader);
            } catch (Exception e) {
                log.info("EUC-KR 실패, UTF-8로 재시도");
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(resource.getInputStream(), Charset.forName("UTF-8")))) {
                    parseCSV(reader);
                }
            }

        } catch (Exception e) {
            log.error("❌ CSV 로드 실패: {}", e.getMessage());
        }

        log.info("═══════════════════════════════════════");
    }

    private void parseCSV(BufferedReader reader) throws Exception {
        // 헤더 읽기
        String header = reader.readLine();
        log.info("📋 CSV 헤더: {}", header);

        // 데이터 파싱
        String line;
        int count = 0;
        while ((line = reader.readLine()) != null) {
            try {
                HospitalInfo hospital = parseCsvLine(line, header);
                if (hospital != null) {
                    allHospitals.add(hospital);
                    count++;
                }
            } catch (Exception e) {
                log.debug("CSV 라인 파싱 오류: {}", e.getMessage());
            }
        }

        log.info("✅ 병원 데이터 로드 완료: {}개", count);
    }

    /**
     * CSV 라인 파싱 (다양한 컬럼 구조 지원)
     */
    private HospitalInfo parseCsvLine(String line, String header) {
        String[] parts = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        String[] headers = header.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

        if (parts.length < 2)
            return null;

        String name = "";
        String address = "";
        String roadAddress = "";
        String phone = "";
        double lat = 0.0;
        double lng = 0.0;
        String specialty = "";

        // 헤더 기반 파싱
        for (int i = 0; i < Math.min(headers.length, parts.length); i++) {
            String h = cleanValue(headers[i]).toLowerCase();
            String v = cleanValue(parts[i]);

            if (h.contains("사업장명") || h.contains("업소명") || h.contains("병원명") || h.contains("name")) {
                name = v;
            } else if (h.contains("소재지전체") || h.contains("주소") && address.isEmpty()) {
                address = v;
            } else if (h.contains("도로명") || h.contains("road")) {
                roadAddress = v;
            } else if (h.contains("전화") || h.contains("phone") || h.contains("연락처")) {
                phone = v;
            } else if (h.contains("위도") || h.contains("lat")) {
                lat = parseDouble(v);
            } else if (h.contains("경도") || h.contains("lng") || h.contains("lon")) {
                lng = parseDouble(v);
            } else if (h.contains("전문") || h.contains("specialty") || h.contains("진료과목")) {
                specialty = v;
            }
        }

        // 이름 없으면 첫 번째 컬럼 사용
        if (name.isEmpty() && parts.length > 0) {
            name = cleanValue(parts[0]);
        }
        if (address.isEmpty() && parts.length > 1) {
            address = cleanValue(parts[1]);
        }
        if (phone.isEmpty() && parts.length > 2) {
            phone = cleanValue(parts[2]);
        }

        if (name.isEmpty() || name.equals("사업장명"))
            return null;

        return HospitalInfo.builder()
                .name(name)
                .address(address)
                .roadAddress(roadAddress.isEmpty() ? address : roadAddress)
                .phone(phone)
                .latitude(lat)
                .longitude(lng)
                .distance(0.0)
                .operatingHours("운영시간 문의")
                .isEmergency(name.contains("24시") || name.contains("응급"))
                .specialty(specialty)
                .build();
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String cleanValue(String value) {
        if (value == null)
            return "";
        return value.replace("\"", "").trim();
    }

    /**
     * 샘플 데이터 로드 (CSV 없을 때)
     * ⭐ 동국대학교 서울캠퍼스(37.5583, 126.9985) 기준 병원 데이터 추가
     */
    private void loadSampleData() {
        log.info("📦 샘플/필수 데이터 로드 (주요 지역 Preset 포함 - 지역당 3개 이상)");

        // === 1. 동국대(서울) - 37.5582, 126.9982 ===
        addHospitalSafe("24시 충무로동물의료센터", "서울특별시 중구 퇴계로 234", "02-2267-7582", 37.5610, 126.9970, true, "피부과,응급,내과");
        addHospitalSafe("을지로펫동물병원", "서울특별시 중구 을지로 100", "02-2266-1234", 37.5660, 126.9910, false, "피부과,안과,치과");
        addHospitalSafe("장충동물병원", "서울특별시 중구 동호로 287", "02-2279-5678", 37.5615, 127.0050, false, "피부과,알러지,내과");
        addHospitalSafe("남산펫클리닉", "서울특별시 중구 소파로 50", "02-2260-3333", 37.5590, 126.9850, false, "피부과,예방접종,미용");

        // === 2. 강남역 - 37.4979, 127.0276 ===
        addHospitalSafe("24시 미래동물병원", "서울특별시 강남구 테헤란로 123", "02-1234-5678", 37.5012, 127.0396, true, "피부과,응급,수술");
        addHospitalSafe("역삼펫클리닉", "서울특별시 강남구 역삼로 200", "02-555-1111", 37.4990, 127.0300, false, "피부과,치과,미용");
        addHospitalSafe("강남피부전문동물병원", "서울특별시 강남구 논현로 789", "02-555-2222", 37.5050, 127.0250, false, "피부과,알러지,아토피");
        addHospitalSafe("서울펫동물병원", "서울특별시 강남구 삼성로 456", "02-2345-6789", 37.5112, 127.0596, false, "피부과,안과,건강검진");

        // === 3. 홍대입구 - 37.5575, 126.9245 ===
        addHospitalSafe("홍대 24시 사랑동물병원", "서울특별시 마포구 양화로 156", "02-333-1111", 37.5570, 126.9240, true, "응급,외과,피부과");
        addHospitalSafe("홍대 라이즈 펫 클리닉", "서울특별시 마포구 홍익로 25", "02-333-2222", 37.5580, 126.9250, false, "피부과,내과,예방접종");
        addHospitalSafe("합정 동물의료센터", "서울특별시 마포구 월드컵로 100", "02-333-3333", 37.5510, 126.9150, false, "피부과,정형외과,재활");

        // === 4. 여의도 - 37.5217, 126.9242 ===
        addHospitalSafe("여의도 IFC 동물병원", "서울특별시 영등포구 국제금융로 10", "02-780-1234", 37.5250, 126.9260, false, "내과,검진,피부과");
        addHospitalSafe("국회의사당 24시 펫케어", "서울특별시 영등포구 의사당대로 1", "02-780-5678", 37.5180, 126.9220, true, "응급,수술,피부과");
        addHospitalSafe("영등포 튼튼 동물병원", "서울특별시 영등포구 당산로 50", "02-780-9999", 37.5300, 126.9100, false, "피부과,치과,노령견케어");

        // === 5. 분당(서현) - 37.3850, 127.1194 ===
        addHospitalSafe("분당 24시 리더스 동물의료원", "경기도 성남시 분당구 황새울로 311", "031-701-1111", 37.3840, 127.1200, true,
                "2차진료,MRI,피부과");
        addHospitalSafe("서현 아프리카 동물병원", "경기도 성남시 분당구 서현로 210", "031-701-2222", 37.3860, 127.1180, false,
                "고양이전문,치과,피부과");
        addHospitalSafe("정자 펫클리닉", "경기도 성남시 분당구 정자일로 100", "031-701-3333", 37.3670, 127.1080, false, "피부과,예방접종,미용");

        // === 6. 부산(해운대) - 35.1587, 129.1603 ===
        addHospitalSafe("해운대 센텀 24시 동물병원", "부산광역시 해운대구 센텀남대로 35", "051-740-1111", 35.1600, 129.1620, true,
                "응급,노령견,피부과");
        addHospitalSafe("마린시티 동물의료센터", "부산광역시 해운대구 마린시티2로 33", "051-740-2222", 35.1550, 129.1580, false, "피부과,안과,내과");
        addHospitalSafe("광안리 펫클리닉", "부산광역시 수영구 광안해변로 100", "051-740-3333", 35.1530, 129.1180, false, "피부과,치과,예방접종");

        // === 7. 대구(동성로) - 35.8714, 128.6014 ===
        addHospitalSafe("대구 중앙 24시 동물병원", "대구광역시 중구 중앙대로 400", "053-255-1111", 35.8700, 128.6000, true, "응급,외과,피부과");
        addHospitalSafe("반월당 튼튼 동물병원", "대구광역시 중구 달구벌대로 2100", "053-255-2222", 35.8680, 128.5950, false, "예방접종,중성화,피부과");
        addHospitalSafe("동성로 펫케어", "대구광역시 중구 동성로 50", "053-255-3333", 35.8720, 128.5980, false, "피부과,내과,미용");

        // === 8. 대전(시청) - 36.3504, 127.3845 ===
        addHospitalSafe("대전 타임 24시 동물의료센터", "대전광역시 서구 둔산로 100", "042-480-1111", 36.3510, 127.3850, true, "응급,영상의학,피부과");
        addHospitalSafe("둔산 펫 클리닉", "대전광역시 서구 대덕대로 200", "042-480-2222", 36.3550, 127.3800, false, "내과,치과,피부과");
        addHospitalSafe("유성 동물병원", "대전광역시 유성구 대학로 100", "042-480-3333", 36.3620, 127.3560, false, "피부과,예방접종,건강검진");

        // === 9. 광주(터미널) - 35.1601, 126.8793 ===
        addHospitalSafe("광주 유스퀘어 24시 동물병원", "광주광역시 서구 무진대로 904", "062-360-1111", 35.1610, 126.8800, true, "응급,골절,피부과");
        addHospitalSafe("상무지구 닥터펫", "광주광역시 서구 상무중앙로 50", "062-360-2222", 35.1500, 126.8500, false, "피부과,미용,건강검진");
        addHospitalSafe("광주 중앙 동물병원", "광주광역시 동구 충장로 100", "062-360-3333", 35.1480, 126.9150, false, "피부과,내과,치과");

        log.info("📦 샘플/필수 데이터 로드 완료 (총 {}개 - 지역당 3개 이상 보장)", allHospitals.size());
    }

    private void addHospitalSafe(String name, String address, String phone, double lat, double lng, boolean isEmergency,
            String specialty) {
        boolean exists = allHospitals.stream().anyMatch(h -> h.getName().equals(name)
                && Math.abs(h.getLatitude() - lat) < 0.0001 && Math.abs(h.getLongitude() - lng) < 0.0001);

        if (!exists) {
            allHospitals.add(HospitalInfo.builder()
                    .name(name)
                    .address(address)
                    .roadAddress(address)
                    .phone(phone)
                    .latitude(lat)
                    .longitude(lng)
                    .distance(0.0)
                    .operatingHours(isEmergency ? "24시간" : "09:00 - 20:00")
                    .isEmergency(isEmergency)
                    .specialty(specialty)
                    .build());
        }
    }

    /**
     * 현재 위치 기반 가까운 병원 찾기 (거리 계산)
     *
     * @param latitude  현재 위도
     * @param longitude 현재 경도
     * @param radiusKm  반경 (km)
     * @return 거리순 정렬된 병원 목록
     */
    public List<HospitalInfo> findNearby(double latitude, double longitude, double radiusKm) {
        return allHospitals.stream()
                .filter(h -> h.getLatitude() != 0 && h.getLongitude() != 0)
                .map(h -> {
                    double dist = calculateDistance(latitude, longitude, h.getLatitude(), h.getLongitude());
                    return HospitalInfo.builder()
                            .name(h.getName())
                            .address(h.getAddress())
                            .roadAddress(h.getRoadAddress())
                            .phone(h.getPhone())
                            .latitude(h.getLatitude())
                            .longitude(h.getLongitude())
                            .distance(Math.round(dist * 100.0) / 100.0)
                            .operatingHours(h.getOperatingHours())
                            .isEmergency(h.isEmergency())
                            .specialty(h.getSpecialty())
                            .build();
                })
                .filter(h -> h.getDistance() <= radiusKm)
                .sorted(Comparator.comparingDouble(HospitalInfo::getDistance))
                .collect(Collectors.toList());
    }

    /**
     * 질병/증상 관련 전문 병원 찾기
     *
     * @param disease 질병/증상 키워드 (예: 피부, 알러지, 관절)
     * @return 전문 병원 목록
     */
    public List<HospitalInfo> findBySpecialty(String disease) {
        String keyword = disease.toLowerCase();

        return allHospitals.stream()
                .filter(h -> {
                    String specialty = h.getSpecialty() != null ? h.getSpecialty().toLowerCase() : "";
                    String name = h.getName().toLowerCase();
                    return specialty.contains(keyword) || name.contains(keyword);
                })
                .collect(Collectors.toList());
    }

    /**
     * 위치 + 질병 기반 병원 추천
     *
     * @param latitude  위도
     * @param longitude 경도
     * @param radiusKm  반경
     * @param disease   질병/증상
     * @return 거리순 정렬된 전문 병원
     */
    public List<HospitalInfo> findNearbyBySpecialty(double latitude, double longitude,
            double radiusKm, String disease) {
        List<HospitalInfo> nearby = findNearby(latitude, longitude, radiusKm);
        String keyword = disease != null ? disease.toLowerCase() : "";

        if (keyword.isEmpty()) {
            return nearby;
        }

        // 전문 병원 우선 + 거리순
        return nearby.stream()
                .sorted((a, b) -> {
                    boolean aMatch = matchesSpecialty(a, keyword);
                    boolean bMatch = matchesSpecialty(b, keyword);
                    if (aMatch && !bMatch)
                        return -1;
                    if (!aMatch && bMatch)
                        return 1;
                    return Double.compare(a.getDistance(), b.getDistance());
                })
                .collect(Collectors.toList());
    }

    private boolean matchesSpecialty(HospitalInfo h, String keyword) {
        String specialty = h.getSpecialty() != null ? h.getSpecialty().toLowerCase() : "";
        String name = h.getName().toLowerCase();
        return specialty.contains(keyword) || name.contains(keyword);
    }

    /**
     * Haversine 공식으로 두 좌표 간 거리 계산 (km)
     */
    private double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371; // 지구 반경 (km)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    // 기존 메서드들
    public List<HospitalInfo> getAllHospitals() {
        return new ArrayList<>(allHospitals);
    }

    public List<HospitalInfo> findByRegion(String region) {
        return allHospitals.stream()
                .filter(h -> h.getAddress().contains(region) ||
                        h.getRoadAddress().contains(region))
                .collect(Collectors.toList());
    }

    public List<HospitalInfo> searchByName(String keyword) {
        return allHospitals.stream()
                .filter(h -> h.getName().contains(keyword))
                .collect(Collectors.toList());
    }

    public List<HospitalInfo> findEmergencyHospitals() {
        return allHospitals.stream()
                .filter(HospitalInfo::isEmergency)
                .collect(Collectors.toList());
    }

    public int getTotalCount() {
        return allHospitals.size();
    }
}
