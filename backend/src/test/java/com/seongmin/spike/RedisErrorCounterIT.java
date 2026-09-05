package com.seongmin.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.seongmin.spike.error.domain.Environment;
import com.seongmin.spike.error.infrastructure.CounterRequest;
import com.seongmin.spike.error.infrastructure.RedisErrorCounter;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

class RedisErrorCounterIT extends IntegrationTestBase {
    @Autowired RedisErrorCounter counter;
    @Autowired StringRedisTemplate redis;

    private static CounterRequest req(long policyId, String fp, int window, Instant at) {
        return new CounterRequest(policyId, PROJECT_ID, Environment.STAGING, fp, window, at);
    }

    private static long policyId() { return ThreadLocalRandom.current().nextLong(1_000_000, Long.MAX_VALUE); }

    @Test
    void countsOnlyInsideWindowAndHandlesBoundarySecond() {
        long pid = policyId();
        String fp = uniqueFp();
        Instant t = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        assertThat(counter.increment(req(pid, fp, 10, t.minusSeconds(10))).count()).isEqualTo(1);
        assertThat(counter.increment(req(pid, fp, 10, t.minusSeconds(9))).count()).isEqualTo(2);
        // now=t → window [t-9, t]: t-10 은 제외, t-9 는 포함
        assertThat(counter.increment(req(pid, fp, 10, t)).count()).isEqualTo(2);
        assertThat(redis.opsForHash().size("ec:" + pid + ":" + fp)).isEqualTo(2);
    }

    @Test
    void expiresAfterWindowPlusGrace() {
        long pid = policyId();
        String fp = uniqueFp();
        counter.increment(req(pid, fp, 1, Instant.now()));
        String key = "ec:" + pid + ":" + fp;
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isBetween(1L, 6L);
        Awaitility.await().atMost(Duration.ofSeconds(9)).untilAsserted(() -> assertThat(redis.hasKey(key)).isFalse());
    }

    @Test
    void concurrentIncrementsAreExact() throws Exception {
        long pid = policyId();
        String fp = uniqueFp();
        int threads = 50, perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<java.util.concurrent.Future<Long>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await();
                long last = 0;
                for (int j = 0; j < perThread; j++) last = counter.increment(req(pid, fp, 60, Instant.now())).count();
                return last;
            }));
        }
        start.countDown();
        long max = 0;
        for (var f : results) max = Math.max(max, f.get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertThat(max).isEqualTo(threads * perThread);
        assertThat(counter.increment(req(pid, fp, 60, Instant.now())).count()).isEqualTo(threads * perThread + 1);
    }

    @Test
    void separatesFingerprints() {
        long pid = policyId();
        String a = uniqueFp(), b = uniqueFp();
        Instant now = Instant.now();
        counter.increment(req(pid, a, 60, now));
        counter.increment(req(pid, a, 60, now));
        assertThat(counter.increment(req(pid, b, 60, now)).count()).isEqualTo(1);
        assertThat(counter.increment(req(pid, a, 60, now)).count()).isEqualTo(3);
    }
}
