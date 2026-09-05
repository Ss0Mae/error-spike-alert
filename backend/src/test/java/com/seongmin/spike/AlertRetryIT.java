package com.seongmin.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.seongmin.spike.alert.application.AlertDispatcher;
import com.seongmin.spike.error.domain.Environment;
import java.time.Duration;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

class AlertRetryIT extends IntegrationTestBase {
    @Autowired AlertDispatcher dispatcher;

    private long trigger(FakeWebhook hook, Consumer<FakeWebhook> setup) {
        setup.accept(hook);
        String fp = uniqueFp();
        createPolicy(fp, 60, 1, 3600, hook.url());
        JsonNode ev = firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
        assertThat(ev.path("result").asText()).isEqualTo("TRIGGERED");
        return ev.path("alertId").asLong();
    }

    private JsonNode awaitStatus(long alertId, String status) {
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(adminGet("/api/alerts/" + alertId).path("status").asText()).isEqualTo(status));
        return adminGet("/api/alerts/" + alertId);
    }

    @Test
    void succeedsOnThirdAttemptAfterTwoServerErrors() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            long id = trigger(hook, h -> h.script(500, 500));
            JsonNode a = awaitStatus(id, "SENT");
            assertThat(a.path("attemptCount").asInt()).isEqualTo(3);
            assertThat(hook.received).hasSize(3);
            assertThat(hook.received.get(2).body()).contains("\"attempt\":3");
        }
    }

    @Test
    void failsAfterMaxAttemptsAndManualRetryWorks() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            long id = trigger(hook, h -> h.script(500, 500, 500));
            JsonNode a = awaitStatus(id, "FAILED");
            assertThat(a.path("attemptCount").asInt()).isEqualTo(3);
            assertThat(a.path("failureReason").asText()).startsWith("RETRY_EXHAUSTED");
            assertThat(hook.received).hasSize(3);

            var retry = admin(HttpMethod.POST, "/api/alerts/" + id + "/retry", null);
            assertThat(retry.getStatusCode().value()).isEqualTo(202);
            JsonNode b = awaitStatus(id, "SENT");
            assertThat(b.path("attemptCount").asInt()).isEqualTo(4);
            assertThat(admin(HttpMethod.POST, "/api/alerts/" + id + "/retry", null).getStatusCode().value()).isEqualTo(409);
        }
    }

    @Test
    void doesNotRetryClientErrors() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            long id = trigger(hook, h -> h.script(404));
            JsonNode a = awaitStatus(id, "FAILED");
            assertThat(a.path("attemptCount").asInt()).isEqualTo(1);
            assertThat(a.path("failureReason").asText()).startsWith("NON_RETRYABLE: HTTP 404");
            Thread.sleep(300);
            assertThat(hook.received).hasSize(1);
        }
    }

    @Test
    void retriesTimeoutsAndRateLimitsRespectingRetryAfter() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            long id = trigger(hook, h -> h.delayFirst(1, 1500)); // read timeout 500ms
            JsonNode a = awaitStatus(id, "SENT");
            assertThat(a.path("attemptCount").asInt()).isEqualTo(2);
            Awaitility.await().atMost(Duration.ofSeconds(3)).untilAsserted(() -> assertThat(hook.received).hasSize(2));
        }
        try (FakeWebhook hook = new FakeWebhook()) {
            long started = System.currentTimeMillis();
            long id = trigger(hook, h -> h.script(429)); // Retry-After: 1
            JsonNode a = awaitStatus(id, "SENT");
            assertThat(a.path("attemptCount").asInt()).isEqualTo(2);
            assertThat(hook.received.get(1).at().toEpochMilli() - hook.received.get(0).at().toEpochMilli()).isGreaterThanOrEqualTo(1000);
            assertThat(System.currentTimeMillis() - started).isGreaterThanOrEqualTo(1000);
        }
    }

    @Test
    void dispatchingTheSameAlertTwiceReachesWebhookTwiceWithSameIdempotencyKey() throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            long id = trigger(hook, h -> { });
            awaitStatus(id, "SENT");
            dispatcher.dispatch(id); // 타임아웃 후 재시도·수동 retry 가 만들 수 있는 상황 재현
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(hook.received).hasSize(2));
            assertThat(hook.received.get(0).idempotencyKey()).isEqualTo("alert-" + id);
            assertThat(hook.received.get(1).idempotencyKey()).isEqualTo("alert-" + id);
            assertThat(hook.duplicates()).isEqualTo(1); // 수신 측이 이 키로 중복을 걸러야 한다
        }
    }
}
