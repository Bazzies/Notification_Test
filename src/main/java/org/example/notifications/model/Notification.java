package org.example.notifications.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String url;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;
    
    @Column(nullable = false)
    private Integer lastStatus;
    
    @Column(nullable = false)
    private Integer lastLatency;
    
    @Column(nullable = false)
    private Instant detectedAt;
    
    @Column(nullable = false)
    private Instant createdAt;
    
    @Column
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public enum NotificationStatus {
        OPEN,    // 새로 감지된 비정상 상태 (아직 통지 안 됨)
        ACK,     // 통지가 이루어진 상태
        RESOLVED // 상태가 정상으로 복구됨
    }
}

