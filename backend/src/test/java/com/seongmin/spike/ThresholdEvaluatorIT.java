package com.seongmin.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.seongmin.spike.error.domain.Environment;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

class ThresholdEvaluatorIT extends IntegrationTestBase {

    @Test
    void alertsExactlyAtThresholdAndRespectsCooldown() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            String fp = uniqueFp();
            long policyId = createPolicy(fp, 60, 5, 2, hook.url());

            for (int i = 1; i <= 4; i++) {
                var ev = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
                assertThat(ev.path("result").asText()).as("event %d", i).isEqualTo("NOT_TRIGGERED");
                assertThat(ev.path("count").asLong()).isEqualTo(i);
            }
            var fifth = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
            assertThat(fifth.path("result").asText()).isEqualTo("TRIGGERED");
            assertThat(fifth.path("path").asText()).isEqualTo("REDIS");
            long alertId = fifth.path("alertId").asLong();

            var sixth = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
            assertThat(sixth.path("result").asText()).isEqualTo("SUPPRESSED");
            assertThat(adminGet("/api/alerts/cooldowns?projectId=1").toString()).contains("\"policyId\":" + policyId);

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertThat(adminGet("/api/alerts/" + alertId).path("status").asText()).isEqualTo("SENT"));
            assertThat(hook.received).hasSize(1);
            assertThat(hook.received.get(0).idempotencyKey()).isEqualTo("alert-" + alertId);
            assertThat(hook.received.get(0).body()).contains("\"detectedCount\":5").contains("\"threshold\":5");

            Thread.sleep(2_300); // cooldown 2s 만료
            var seventh = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
            assertThat(seventh.path("result").asText()).isEqualTo("TRIGGERED");
            assertThat(seventh.path("count").asLong()).isEqualTo(7);

            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(hook.received).hasSize(2));
            assertThat(adminGet("/api/alerts?projectId=1&policyId=" + policyId).path("totalElements").asLong()).isEqualTo(2);
        }
    }
}
