package com.seongmin.spike.common.monitoring;

import com.seongmin.spike.common.response.ApiResponse;
import com.seongmin.spike.error.infrastructure.DatabaseErrorCounter;
import com.seongmin.spike.error.infrastructure.RedisHealthGuard;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {
    private final DetectionState state;
    private final RedisHealthGuard guard;
    private final DatabaseErrorCounter dbCounter;
    private final ThreadPoolTaskExecutor executor;

    public SystemStatusController(DetectionState state, RedisHealthGuard guard, DatabaseErrorCounter dbCounter,
                                  @Qualifier("alertExecutor") ThreadPoolTaskExecutor executor) {
        this.state = state; this.guard = guard; this.dbCounter = dbCounter; this.executor = executor;
    }

    public record Breaker(String state, Instant openUntil, boolean forced) {}
    public record Executor(int active, int queueSize, int queueCapacity, int poolSize) {}
    public record Status(String detectionMode, Breaker redisBreaker, int fallbackPermitsAvailable, Executor executor) {}
    public record BreakerRequest(boolean forceOpen) {}

    @GetMapping("/status")
    public ApiResponse<Status> status() {
        return ApiResponse.ok(build());
    }

    @PostMapping("/redis-breaker")
    public ApiResponse<Status> breaker(@RequestBody BreakerRequest req) {
        guard.forceOpen(req.forceOpen());
        return ApiResponse.ok(build());
    }

    private Status build() {
        long until = guard.openUntil();
        var tpe = executor.getThreadPoolExecutor();
        return new Status(state.mode().name(),
                new Breaker(guard.state().name(), until == 0 ? null : Instant.ofEpochMilli(until), guard.isForced()),
                dbCounter.availablePermits(),
                new Executor(tpe.getActiveCount(), tpe.getQueue().size(), executor.getQueueCapacity(), tpe.getPoolSize()));
    }
}
