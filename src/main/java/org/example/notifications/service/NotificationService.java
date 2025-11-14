package org.example.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notifications.model.Notification;
import org.example.notifications.model.NotificationLog;
import org.example.notifications.repository.NotificationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final JavaMailSender mailSender;
    private final NotificationLogRepository notificationLogRepository;
    
    @Value("${notification.recipient}")
    private String alertRecipient;
    
    @Value("${notification.retry.max-attempts}")
    private int maxRetryAttempts;
    
    @Value("${notification.retry.interval-seconds}")
    private int retryIntervalSeconds;
    
    @Async
    public void sendAlert(Notification notification) {
        log.info("알림 전송 시작: URL={}, Status={}, Latency={}ms", 
                notification.getUrl(), 
                notification.getLastStatus(), 
                notification.getLastLatency());
        
        boolean success = false;
        String errorMessage = null;
        
        for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(alertRecipient);
                message.setSubject("[ALERT] 대상 URL 상태 비정상");
                
                String body = String.format(
                    "점검 대상: %s\n" +
                    "상태 코드: %d\n" +
                    "응답 시간: %d ms\n" +
                    "감지 시각: %s",
                    notification.getUrl(),
                    notification.getLastStatus(),
                    notification.getLastLatency(),
                    formatTimestamp(notification.getDetectedAt())
                );
                
                message.setText(body);
                
                mailSender.send(message);
                
                success = true;
                log.info("이메일 전송 완료: 수신자={}, 시각={}", 
                        alertRecipient, 
                        formatTimestamp(Instant.now()));
                
                // 수신 확인 로그
                log.info("Email successfully received at {}", formatTimestamp(Instant.now()));
                
                break;
                
            } catch (Exception e) {
                errorMessage = e.getMessage();
                log.warn("이메일 전송 실패 (시도 {}/{}): {}", 
                        attempt, maxRetryAttempts, errorMessage);
                
                if (attempt < maxRetryAttempts) {
                    try {
                        Thread.sleep(retryIntervalSeconds * 1000L);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        
        // NotificationLog 저장
        NotificationLog logEntry = new NotificationLog();
        logEntry.setNotificationId(notification.getId());
        logEntry.setStatus(success ? 
                NotificationLog.NotificationStatus.SENT : 
                NotificationLog.NotificationStatus.FAILED);
        logEntry.setErrorMessage(errorMessage);
        logEntry.setSentAt(Instant.now());
        
        notificationLogRepository.save(logEntry);
        log.info("NotificationLog 저장됨: Status={}, NotificationId={}", 
                logEntry.getStatus(), notification.getId());
    }
    
    private String formatTimestamp(Instant instant) {
        return instant.atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_INSTANT);
    }
}

