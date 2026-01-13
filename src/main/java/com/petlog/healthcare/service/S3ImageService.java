package com.petlog.healthcare.service;

import com.petlog.healthcare.config.S3Config.S3Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * AWS S3 Image Storage Service
 *
 * 피부질환 이미지를 S3에 저장
 *
 * @author healthcare-team
 * @since 2026-01-07
 */
@Slf4j
@Service
public class S3ImageService {

    private final S3Client s3Client;
    private final S3Properties s3Properties;

    public S3ImageService(S3Client s3Client, S3Properties s3Properties) {
        this.s3Client = s3Client;
        this.s3Properties = s3Properties;
    }

    /**
     * 이미지 S3 업로드
     *
     * @param image  업로드할 이미지
     * @param folder 저장 폴더 (예: skin-disease, profile)
     * @return S3 URL
     */
    public String uploadImage(MultipartFile image, String folder) {
        if (s3Client == null) {
            log.warn("⚠️ S3 Client 미설정 - 업로드 스킵");
            return null;
        }

        try {
            String key = generateKey(folder, image.getOriginalFilename());

            log.info("📤 S3 업로드 시작: {}", key);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(image.getContentType())
                    .acl(ObjectCannedACL.PUBLIC_READ) // ⭐ Meshy API가 이미지 다운로드할 수 있도록 공개 설정
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(image.getBytes()));

            String url = getObjectUrl(key);
            log.info("✅ S3 업로드 완료: {}", url);

            return url;

        } catch (IOException e) {
            log.error("❌ S3 업로드 실패: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 이미지 키 생성
     * 형식: {folder}/{date}/{uuid}_{filename}
     */
    private String generateKey(String folder, String originalFilename) {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        String filename = originalFilename != null ? originalFilename : "image.jpg";

        return String.format("%s/%s/%s_%s", folder, date, uuid, filename);
    }

    /**
     * S3 객체 URL 조회
     */
    private String getObjectUrl(String key) {
        return String.format("https://%s.s3.%s.amazonaws.com/%s",
                s3Properties.getBucketName(),
                s3Properties.getRegion(),
                key);
    }

    /**
     * 바이트 배열로 업로드
     */
    public String uploadBytes(byte[] bytes, String folder, String filename, String contentType) {
        if (s3Client == null) {
            log.warn("⚠️ S3 Client 미설정 - 업로드 스킵");
            return null;
        }

        try {
            String key = generateKey(folder, filename);

            log.info("📤 S3 업로드 시작 (bytes): {}", key);

            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(s3Properties.getBucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(request, RequestBody.fromBytes(bytes));

            String url = getObjectUrl(key);
            log.info("✅ S3 업로드 완료: {}", url);

            return url;

        } catch (Exception e) {
            log.error("❌ S3 업로드 실패: {}", e.getMessage(), e);
            return null;
        }
    }
}
