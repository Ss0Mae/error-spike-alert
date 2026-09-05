package com.seongmin.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.seongmin.spike.error.domain.Environment;
import com.seongmin.spike.error.infrastructure.DatabaseErrorCounter;
import com.seongmin.spike.error.infrastructure.RedisHealthGuard;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FallbackIT extends IntegrationTestBase {
    @Autowired RedisHealthGuard guard;
    @Autowired DatabaseErrorCounter dbCounter;

    @AfterEach
    void restore() {
        guard.forceOpen(false);
    }

    @Test
    void switchesToDatabaseWhenRedisIsPausedAndBackWhenItRecovers() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            String fp = uniqueFp();
            long policyId = createPolicy(fp, 60, 3, 3600, hook.url());
            REDIS.getDockerClient().pauseContainerCmd(REDIS.getContainerId()).exec();
            try {
                JsonNode first = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
                assertThat(first.path("path").asText()).isEqualTo("DB_FALLBACK");
                assertThat(first.path("count").asLong()).isEqualTo(1);
                JsonNode status = adminGet("/api/system/status");
                assertThat(status.path("redisBreaker").path("state").asText()).isEqualTo("OPEN");
                // 같은 컨텍스트의 다른 테스트가 5초 안에 감지를 생략했으면 DEGRADED 로 보일 수 있다
                assertThat(status.path("detectionMode").asText()).isIn("FALLBACK", "DEGRADED");
                ingest(API_KEY, event(Environment.STAGING, fp));
                JsonNode third = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
                assertThat(third.path("result").asText()).isEqualTo("TRIGGERED");
                assertThat(third.path("path").asText()).isEqualTo("DB_FALLBACK");
                long alertId = third.path("alertId").asLong();
                Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                        assertThat(adminGet("/api/alerts/" + alertId).path("status").asText()).isEqualTo("SENT"));
                assertThat(adminGet("/api/alerts/" + alertId).path("detectionPath").asText()).isEqualTo("DB_FALLBACK");
                assertThat(firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp))).path("result").asText()).isEqualTo("SUPPRESSED");
            } finally {
                REDIS.getDockerClient().unpauseContainerCmd(REDIS.getContainerId()).exec();
            }
            Awaitility.await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                    assertThat(firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp))).path("path").asText()).isEqualTo("REDIS"));
            assertThat(adminGet("/api/system/status").path("redisBreaker").path("state").asText()).isEqualTo("CLOSED");
            assertThat(adminGet("/api/alerts?projectId=1&policyId=" + policyId).path("totalElements").asLong()).isEqualTo(1);
        }
    }

    @Test
    void exhaustedPermitsSkipDetectionButKeepIngesting() {
        String fp = uniqueFp();
        createPolicy(fp, 60, 1, 3600, "http://127.0.0.1:1/webhook");
        guard.forceOpen(true);
        int drained = dbCounter.permits().drainPermits();
        try {
            var res = ingest(API_KEY, event(Environment.STAGING, fp));
            assertThat(res.getStatusCode().value()).isEqualTo(202);
            JsonNode ev = firstEvaluation(res);
            assertThat(ev.path("result").asText()).isEqualTo("SKIPPED");
            assertThat(ev.path("path").asText()).isEqualTo("NONE");
            long errorId = json(res).path("data").path("errorId").asLong();
            assertThat(adminGet("/api/errors/" + errorId).path("fingerprint").asText()).isEqualTo(fp);
            JsonNode status = adminGet("/api/system/status");
            assertThat(status.path("detectionMode").asText()).isEqualTo("DEGRADED");
            assertThat(status.path("fallbackPermitsAvailable").asInt()).isZero();
        } finally {
            dbCounter.permits().release(drained);
        }
        JsonNode ev = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
        assertThat(ev.path("path").asText()).isEqualTo("DB_FALLBACK");
        assertThat(ev.path("count").asLong()).isEqualTo(2);
        assertThat(ev.path("result").asText()).isEqualTo("TRIGGERED");
    }
}
