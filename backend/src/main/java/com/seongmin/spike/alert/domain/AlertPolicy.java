package com.seongmin.spike.alert.domain;

import com.seongmin.spike.error.domain.Environment;
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
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "alert_policies")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AlertPolicy {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long projectId;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private Environment environment;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    private PolicyScope scope;
    private String targetFingerprint;
    private int windowSeconds;
    private int threshold;
    private int cooldownSeconds;
    private String channel;
    private String webhookUrl;
    private boolean enabled;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean matches(Environment env, String fingerprint) {
        return enabled && environment == env && (targetFingerprint == null || targetFingerprint.equals(fingerprint));
    }

    /** 카운터·cooldown 키에 쓰는 fingerprint 부분. ALL_ERRORS 는 "*". */
    public String fpKey(String fingerprint) {
        return scope == PolicyScope.PER_FINGERPRINT ? fingerprint : "*";
    }

    /** {policyId}:{fpKey}:{slot}. slot = cooldown 길이의 고정 슬롯 → Redis 없이도 UNIQUE 로 중복 차단. */
    public String dedupKey(String fpKey, Instant at) {
        long slot = cooldownSeconds > 0 ? at.getEpochSecond() / cooldownSeconds : at.toEpochMilli();
        return id + ":" + fpKey + ":" + slot;
    }
}
