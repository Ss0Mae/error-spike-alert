package com.seongmin.spike.error.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "error_events")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ErrorEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long projectId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private Environment environment;
    private String fingerprint;
    private String errorType;
    private String message;
    @Column(columnDefinition = "MEDIUMTEXT")
    private String stackTrace;
    private Instant occurredAt;
    private Instant receivedAt;
    private String eventId;
    private String requestId;
    private String traceId;
    private String serverInstance;
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> metadata;
    private Instant createdAt;
}
