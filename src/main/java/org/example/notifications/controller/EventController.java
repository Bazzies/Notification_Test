package org.example.notifications.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.notifications.dto.EventRequest;
import org.example.notifications.model.Event;
import org.example.notifications.service.EventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Slf4j
public class EventController {
    
    private final EventService eventService;
    
    @Value("${api.key}")
    private String apiKey;
    
    @PostMapping
    public ResponseEntity<?> createEvent(
            @RequestHeader(value = "x-api-key", required = false) String requestApiKey,
            @Valid @RequestBody EventRequest request) {
        
        // API 키 검증
        if (requestApiKey == null || !requestApiKey.equals(apiKey)) {
            log.warn("API 키 검증 실패: 제공된 키={}", requestApiKey);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid API key");
        }
        
        // 데이터 검증
        try {
            Instant timestamp = Instant.parse(request.getTimestamp());
            
            // 비정상 값 체크
            if (request.getStatus() < 100 || request.getStatus() >= 600) {
                return ResponseEntity.badRequest()
                        .body("Invalid HTTP status code");
            }
            
            if (request.getLatency() < 0) {
                return ResponseEntity.badRequest()
                        .body("Latency must be non-negative");
            }
            
            // Event 엔티티 생성
            Event event = new Event();
            event.setUrl(request.getUrl());
            event.setStatus(request.getStatus());
            event.setLatency(request.getLatency());
            event.setTimestamp(timestamp);
            
            // 이벤트 처리
            eventService.processEvent(event);
            
            log.info("이벤트 수신 및 처리 완료: URL={}, Status={}, Latency={}ms", 
                    request.getUrl(), request.getStatus(), request.getLatency());
            
            return ResponseEntity.status(HttpStatus.CREATED).body("Event processed successfully");
            
        } catch (Exception e) {
            log.error("이벤트 처리 중 오류 발생", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error processing event: " + e.getMessage());
        }
    }
}

