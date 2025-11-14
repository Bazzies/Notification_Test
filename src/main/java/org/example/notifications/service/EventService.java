package org.example.notifications.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notifications.model.Event;
import org.example.notifications.model.Notification;
import org.example.notifications.repository.EventRepository;
import org.example.notifications.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventService {
    
    private final EventRepository eventRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    
    @Value("${notification.latency-threshold}")
    private int latencyThreshold;
    
    @Transactional
    public void processEvent(Event event) {
        // 이벤트 저장
        eventRepository.save(event);
        log.debug("이벤트 저장됨: URL={}, Status={}, Latency={}ms", 
                event.getUrl(), event.getStatus(), event.getLatency());
        
        // 비정상 상태 판별
        boolean isAbnormal = (event.getStatus() != 200) || (event.getLatency() >= latencyThreshold);
        
        // 기존 알림 상태 조회
        Optional<Notification> existingNotification = notificationRepository.findByUrl(event.getUrl());
        
        if (isAbnormal) {
            handleAbnormalEvent(event, existingNotification);
        } else {
            handleNormalEvent(event, existingNotification);
        }
    }
    
    private void handleAbnormalEvent(Event event, Optional<Notification> existingNotification) {
        if (existingNotification.isPresent()) {
            Notification notification = existingNotification.get();
            
            // 이미 ACK 상태이고 동일한 비정상 상태면 중복 알림 방지
            if (notification.getStatus() == Notification.NotificationStatus.ACK &&
                notification.getLastStatus().equals(event.getStatus()) &&
                notification.getLastLatency().equals(event.getLatency())) {
                log.debug("중복 알림 방지: URL={}, Status={}", event.getUrl(), event.getStatus());
                return;
            }
            
            // 상태 업데이트
            notification.setLastStatus(event.getStatus());
            notification.setLastLatency(event.getLatency());
            notification.setDetectedAt(event.getTimestamp());
            
            // OPEN 상태로 변경 (새로운 비정상 감지)
            if (notification.getStatus() == Notification.NotificationStatus.RESOLVED) {
                notification.setStatus(Notification.NotificationStatus.OPEN);
            }
            
            notificationRepository.save(notification);
            
            // OPEN 상태일 때만 알림 전송
            if (notification.getStatus() == Notification.NotificationStatus.OPEN) {
                notification.setStatus(Notification.NotificationStatus.ACK);
                notificationRepository.save(notification);
                notificationService.sendAlert(notification);
            }
        } else {
            // 새로운 비정상 상태 감지
            Notification notification = new Notification();
            notification.setUrl(event.getUrl());
            notification.setStatus(Notification.NotificationStatus.OPEN);
            notification.setLastStatus(event.getStatus());
            notification.setLastLatency(event.getLatency());
            notification.setDetectedAt(event.getTimestamp());
            
            notification = notificationRepository.save(notification);
            log.info("비정상 상태 감지: URL={}, Status={}, Latency={}ms", 
                    event.getUrl(), event.getStatus(), event.getLatency());
            
            // 알림 전송
            notification.setStatus(Notification.NotificationStatus.ACK);
            notificationRepository.save(notification);
            notificationService.sendAlert(notification);
        }
    }
    
    private void handleNormalEvent(Event event, Optional<Notification> existingNotification) {
        if (existingNotification.isPresent()) {
            Notification notification = existingNotification.get();
            
            // RESOLVED 상태로 변경
            if (notification.getStatus() != Notification.NotificationStatus.RESOLVED) {
                notification.setStatus(Notification.NotificationStatus.RESOLVED);
                notification.setLastStatus(event.getStatus());
                notification.setLastLatency(event.getLatency());
                notification.setDetectedAt(event.getTimestamp());
                notificationRepository.save(notification);
                log.info("정상 상태로 복구: URL={}", event.getUrl());
            }
        }
    }
}

