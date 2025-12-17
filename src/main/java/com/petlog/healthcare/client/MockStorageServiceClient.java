package com.petlog.record.client;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

// [수정] 빈 이름을 명시적으로 지정
@Profile({"test", "local-test"}) // 'local-test' 프로필이 활성화될 때만 이 빈을 로드함
@Component("mockStorageServiceClient")
public class MockStorageServiceClient implements StorageServiceClient {

    @Override
    public void savePhotos(List<PhotoRequest> photos) {
        // [Mock 구현] 실제 외부 서비스로 HTTP 요청을 보내는 대신,
        // 로컬 테스트 중임을 알리는 로그만 남기고 정상 종료합니다.
        System.out.println("--- [MOCK CALL SUCCESS] ---");
        System.out.println("StorageServiceClient: 외부 보관함 서비스 호출 스킵됨 (local-test 프로필 활성)");
        System.out.println("저장 요청 사진 수: " + photos.size() + "장");
        System.out.println("---------------------------");

        // [추가] URL 정보 로깅
        System.out.println("전송 URL: " + photos.stream().map(PhotoRequest::imageUrl).collect(Collectors.joining(", ")));
        System.out.println("---------------------------");
    }
}