package com.seongmin.spike.alert.domain;

import com.seongmin.spike.error.infrastructure.DetectionPath;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alert_histories")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long alertPolicyId;
    private Long projectId;
    private String fingerprint;
    private int detectedCount;
    private Instant detectedAt;
    private Instant windowStartedAt;
    private Instant windowEndedAt;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private AlertStatus status;
    private int attemptCount;
    private Instant sentAt;
    private String failureReason;
    private String dedupKey;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private DetectionPath detectionPath;
    private Long triggerEventId;
    private Instant createdAt;

    public void markSent(Instant at, int attempts) {
        this.status = AlertStatus.SENT;
        this.sentAt = at;
        this.attemptCount += attempts;
        this.failureReason = null;
    }

    public void markFailed(int attempts, String reason) {
        this.status = AlertStatus.FAILED;
        this.attemptCount += attempts;
        this.failureReason = reason == null ? null : reason.substring(0, Math.min(reason.length(), 500));
    }

    public void markPending() {
        this.status = AlertStatus.PENDING;
    }
}
