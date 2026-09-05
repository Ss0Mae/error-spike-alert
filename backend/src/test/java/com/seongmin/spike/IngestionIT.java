package com.seongmin.spike;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.seongmin.spike.error.domain.Environment;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class IngestionIT extends IntegrationTestBase {

    @Test
    void rejectsMissingOrUnknownApiKey() {
        assertThat(ingest(null, event(Environment.STAGING, uniqueFp())).getStatusCode().value()).isEqualTo(401);
        ResponseEntity<String> res = ingest("nope", event(Environment.STAGING, uniqueFp()));
        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(json(res).path("error").path("code").asText()).isEqualTo("INVALID_API_KEY");
    }

    @Test
    void rejectsInvalidBody() {
        Map<String, Object> body = event(Environment.STAGING, uniqueFp());
        body.remove("errorType");
        ResponseEntity<String> res = ingest(API_KEY, body);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(json(res).path("error").path("code").asText()).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void duplicateEventIdIsIdempotentAndNotCounted() {
        String fp = uniqueFp();
        createPolicy(fp, 60, 100, 0, "http://127.0.0.1:1/webhook");
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> body = event(Environment.STAGING, fp);
        body.put("eventId", eventId);

        ResponseEntity<String> first = ingest(API_KEY, body);
        assertThat(first.getStatusCode().value()).isEqualTo(202);
        assertThat(firstEvaluation(first).path("count").asLong()).isEqualTo(1);

        ResponseEntity<String> again = ingest(API_KEY, body);
        assertThat(again.getStatusCode().value()).isEqualTo(200);
        JsonNode data = json(again).path("data");
        assertThat(data.path("duplicate").asBoolean()).isTrue();
        assertThat(data.path("errorId").asLong()).isEqualTo(json(first).path("data").path("errorId").asLong());
        assertThat(data.path("evaluations")).isEmpty();

        ResponseEntity<String> third = ingest(API_KEY, event(Environment.STAGING, fp));
        assertThat(firstEvaluation(third).path("count").asLong()).isEqualTo(2);
    }

    @Test
    void generatesStableFingerprintWhenAbsent() {
        Map<String, Object> a = event(Environment.DEV, null);
        a.put("message", "Order 12345 not found for user 9f1c2a3b-1111-2222-3333-444444444444");
        a.put("stackTrace", "java.lang.IllegalStateException: x\n\tat java.base/java.util.Objects.requireNonNull(Objects.java:1)\n\tat com.acme.OrderService.find(OrderService.java:42)");
        Map<String, Object> b = event(Environment.DEV, null);
        b.put("message", "Order 777 not found for user 00000000-aaaa-bbbb-cccc-dddddddddddd");
        b.put("stackTrace", "java.lang.IllegalStateException: y\n\tat java.base/java.util.Objects.requireNonNull(Objects.java:9)\n\tat com.acme.OrderService.find(OrderService.java:99)");
        String fa = json(ingest(API_KEY, a)).path("data").path("fingerprint").asText();
        String fb = json(ingest(API_KEY, b)).path("data").path("fingerprint").asText();
        assertThat(fa).hasSize(32).matches("[0-9a-f]+").isEqualTo(fb);

        Map<String, Object> c = event(Environment.DEV, null);
        c.put("errorType", "java.lang.NullPointerException");
        assertThat(json(ingest(API_KEY, c)).path("data").path("fingerprint").asText()).isNotEqualTo(fa);
    }

    @Test
    void detailAndListAreAdminOnly() {
        String fp = uniqueFp();
        long id = json(ingest(API_KEY, event(Environment.DEV, fp))).path("data").path("errorId").asLong();
        assertThat(rest.getForEntity("/api/errors/" + id, String.class).getStatusCode().value()).isEqualTo(401);
        JsonNode detail = adminGet("/api/errors/" + id);
        assertThat(detail.path("fingerprint").asText()).isEqualTo(fp);
        JsonNode list = adminGet("/api/errors?projectId=1&fingerprint=" + fp);
        assertThat(list.path("totalElements").asLong()).isEqualTo(1);
        assertThat(list.path("content").get(0).has("stackTrace")).isFalse();
        JsonNode trend = adminGet("/api/errors/trend?projectId=1&fingerprint=" + fp + "&interval=1m");
        assertThat(trend.path("buckets").size()).isGreaterThanOrEqualTo(1);
        assertThat(trend.path("recent").path("1m").asLong()).isGreaterThanOrEqualTo(1);
    }
}
