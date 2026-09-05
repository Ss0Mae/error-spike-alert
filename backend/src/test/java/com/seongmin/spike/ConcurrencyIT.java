package com.seongmin.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.seongmin.spike.error.domain.Environment;
import com.seongmin.spike.error.infrastructure.RedisHealthGuard;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConcurrencyIT extends IntegrationTestBase {
    @Autowired RedisHealthGuard guard;

    @AfterEach
    void restore() { guard.forceOpen(false); }

    @Test
    void hundredThreadsCrossThresholdProduceExactlyOneAlert() throws Exception {
        runBurst("REDIS");
    }

    @Test
    void withoutRedisTheUniqueDedupKeyStillYieldsOneAlert() throws Exception {
        guard.forceOpen(true);
        runBurst("DB_FALLBACK");
    }

    private void runBurst(String expectedPath) throws Exception {
        try (FakeWebhook hook = new FakeWebhook()) {
            String fp = uniqueFp();
            long policyId = createPolicy(fp, 60, 20, 3600, hook.url());
            int threads = 100;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads), go = new CountDownLatch(1);
            List<Future<JsonNode>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return firstEvaluation(ingest(API_KEY, event(Environment.STAGING, fp)));
                }));
            }
            ready.await();
            go.countDown();
            int triggered = 0, suppressed = 0, skipped = 0;
            for (Future<JsonNode> f : futures) {
                JsonNode ev = f.get(60, TimeUnit.SECONDS);
                switch (ev.path("result").asText()) {
                    case "TRIGGERED" -> { triggered++; assertThat(ev.path("path").asText()).isEqualTo(expectedPath); }
                    case "SUPPRESSED" -> suppressed++;
                    case "SKIPPED" -> skipped++;
                    default -> { }
                }
            }
            pool.shutdown();
            assertThat(triggered).as("triggered (suppressed=%d skipped=%d)", suppressed, skipped).isEqualTo(1);
            assertThat(adminGet("/api/errors?projectId=1&fingerprint=" + fp).path("totalElements").asLong()).isEqualTo(threads);
            assertThat(adminGet("/api/alerts?projectId=1&policyId=" + policyId).path("totalElements").asLong()).isEqualTo(1);
            Awaitility.await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(hook.received).hasSize(1));
            Thread.sleep(500);
            assertThat(hook.received).hasSize(1);
            assertThat(hook.duplicates()).isZero();
        }
    }
}
