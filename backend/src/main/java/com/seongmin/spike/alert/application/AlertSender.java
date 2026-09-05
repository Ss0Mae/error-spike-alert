package com.seongmin.spike.alert.application;

import com.seongmin.spike.common.config.SpikeProperties;
import com.seongmin.spike.common.monitoring.Metrics;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.retry.RetryContext;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * webhook 1회 발송 + @Retryable. 반드시 다른 빈(AlertDispatcher)에서 호출해야 프록시가 적용된다.
 * 같은 클래스 안에서 this.send() 를 부르면 재시도가 일어나지 않는다.
 */
@Slf4j
@Component
public class AlertSender {
    public record SendResult(boolean success, int attempts, String failureReason) {}

    private final RestClient client;
    private final Metrics metrics;
    private final int maxAttempts;
    private final long maxRetryAfterMs;

    public AlertSender(RestClient webhookRestClient, Metrics metrics, SpikeProperties props) {
        this.client = webhookRestClient;
        this.metrics = metrics;
        this.maxAttempts = props.alert().retry().maxAttempts();
        this.maxRetryAfterMs = props.alert().webhook().maxRetryAfterSeconds() * 1000L;
    }

    @Retryable(retryFor = RetryableAlertException.class,
            maxAttemptsExpression = "${spike.alert.retry.max-attempts}",
            backoff = @Backoff(delayExpression = "${spike.alert.retry.backoff-ms}",
                    multiplierExpression = "${spike.alert.retry.multiplier}"))
    public SendResult send(String url, long alertId, WebhookPayload payload) {
        RetryContext ctx = RetrySynchronizationManager.getContext();
        int attempt = (ctx == null ? 0 : ctx.getRetryCount()) + 1;
        if (attempt > 1) metrics.alertRetry.increment();
        Timer.Sample sample = Timer.start();
        try {
            client.post().uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Idempotency-Key", "alert-" + alertId)
                    .body(payload.withAttempt(attempt))
                    .retrieve().toBodilessEntity();
            return new SendResult(true, attempt, null);
        } catch (RestClientResponseException e) {
            int code = e.getStatusCode().value();
            if (code == 429 || code == 408 || code >= 500) {
                if (code == 429) honorRetryAfter(e.getResponseHeaders() == null ? null : e.getResponseHeaders().getFirst("Retry-After"));
                throw new RetryableAlertException("HTTP " + code);
            }
            throw new NonRetryableAlertException("HTTP " + code);
        } catch (ResourceAccessException e) {
            throw new RetryableAlertException("IO: " + e.getMessage());
        } finally {
            sample.stop(metrics.sendDuration);
        }
    }

    @Recover
    public SendResult recover(RetryableAlertException e, String url, long alertId, WebhookPayload payload) {
        return new SendResult(false, maxAttempts, "RETRY_EXHAUSTED: " + e.getMessage());
    }

    /** 4xx: 재시도 없이 1회 실패. (@Recover 가 없으면 Spring Retry 가 ExhaustedRetryException 으로 감싸 버린다.) */
    @Recover
    public SendResult recover(NonRetryableAlertException e, String url, long alertId, WebhookPayload payload) {
        return new SendResult(false, 1, "NON_RETRYABLE: " + e.getMessage());
    }

    /** 429 Retry-After(초) 를 상한(max-retry-after-seconds) 안에서 존중한다. ponytail: 스레드 sleep, 풀이 bounded 라 상한으로 충분. */
    private void honorRetryAfter(String header) {
        if (header == null) return;
        try {
            long ms = Math.min(Long.parseLong(header.trim()) * 1000L, maxRetryAfterMs);
            if (ms > 0) Thread.sleep(ms);
        } catch (NumberFormatException ignored) {
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
