package com.seongmin.spike.alert.infrastructure;

import com.seongmin.spike.common.config.SpikeProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;

@Configuration
@EnableAsync
@EnableRetry
@EnableScheduling
public class AsyncConfig {

    /** bounded 전용 executor. 큐가 차면 AbortPolicy → 호출자가 FAILED(EXECUTOR_SATURATED) 로 기록. CallerRuns 는 수집 API 를 막으므로 쓰지 않는다. */
    @Bean(name = "alertExecutor")
    public ThreadPoolTaskExecutor alertExecutor(SpikeProperties props, MeterRegistry registry) {
        SpikeProperties.Alert.Executor cfg = props.alert().executor();
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(cfg.corePoolSize());
        ex.setMaxPoolSize(cfg.maxPoolSize());
        ex.setQueueCapacity(cfg.queueCapacity());
        ex.setThreadNamePrefix("alert-");
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        ex.setWaitForTasksToCompleteOnShutdown(false);
        ex.initialize();
        Gauge.builder("async.executor.active.threads", ex, e -> e.getThreadPoolExecutor().getActiveCount()).register(registry);
        Gauge.builder("async.executor.queue.size", ex, e -> e.getThreadPoolExecutor().getQueue().size()).register(registry);
        return ex;
    }

    @Bean
    // Boot 가 만든 RestClient.Builder 를 써야 Boot ObjectMapper(ISO-8601 Instant) 가 적용된다. RestClient.builder() 는 Instant 를 epoch 숫자로 직렬화한다.
    public RestClient webhookRestClient(SpikeProperties props, RestClient.Builder builder) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofMillis(props.alert().webhook().connectTimeoutMs()));
        f.setReadTimeout(Duration.ofMillis(props.alert().webhook().readTimeoutMs()));
        return builder.requestFactory(f).build();
    }
}
