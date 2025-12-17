package com.petlog.record.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

// Notification service 호출
@FeignClient(name = "notification-service", url = "${external.notification-service.url}")
public interface NotificationServiceClient {

    @PostMapping("/api/notifications/send")
    void sendNotification(@RequestBody NotificationRequest request);

    record NotificationRequest(Long userId, String title, String message, String type) {}
}