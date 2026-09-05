package com.seongmin.spike;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seongmin.spike.alert.domain.PolicyScope;
import com.seongmin.spike.error.domain.Environment;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

/** 실제 MySQL 8 + Redis 7 (Testcontainers). 컨테이너는 JVM 당 한 번만 뜨고 Ryuk 이 정리한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class IntegrationTestBase {
    protected static final String API_KEY = "demo-api-key";
    protected static final String ADMIN = "admin-token";
    protected static final long PROJECT_ID = 1L;

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUrlParam("connectionTimeZone", "UTC").withUrlParam("forceConnectionTimeZoneToSession", "true");
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired protected TestRestTemplate rest;
    @Autowired protected ObjectMapper om;

    protected static String uniqueFp() { return UUID.randomUUID().toString().replace("-", ""); }

    protected JsonNode json(ResponseEntity<String> res) {
        try { return om.readTree(res.getBody()); } catch (Exception e) { throw new RuntimeException(e); }
    }

    protected ResponseEntity<String> ingest(String apiKey, Map<String, Object> body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null) h.set("X-API-Key", apiKey);
        return rest.exchange("/api/errors", HttpMethod.POST, new HttpEntity<>(body, h), String.class);
    }

    protected Map<String, Object> event(Environment env, String fingerprint) {
        Map<String, Object> m = new HashMap<>();
        m.put("environment", env.name());
        m.put("errorType", "java.lang.IllegalStateException");
        m.put("message", "boom " + fingerprint);
        if (fingerprint != null) m.put("fingerprint", fingerprint);
        return m;
    }

    /** 테스트 격리: STAGING + targetFingerprint 로 이 테스트의 이벤트만 매칭되는 정책. */
    protected long createPolicy(String targetFingerprint, int windowSeconds, int threshold, int cooldownSeconds, String webhookUrl) {
        Map<String, Object> body = new HashMap<>();
        body.put("projectId", PROJECT_ID);
        body.put("environment", Environment.STAGING.name());
        body.put("scope", PolicyScope.PER_FINGERPRINT.name());
        body.put("targetFingerprint", targetFingerprint);
        body.put("windowSeconds", windowSeconds);
        body.put("threshold", threshold);
        body.put("cooldownSeconds", cooldownSeconds);
        body.put("channel", "WEBHOOK");
        body.put("webhookUrl", webhookUrl);
        body.put("enabled", true);
        ResponseEntity<String> res = admin(HttpMethod.POST, "/api/alert-policies", body);
        if (res.getStatusCode().value() != 201) throw new AssertionError("policy create failed: " + res.getBody());
        return json(res).path("data").path("id").asLong();
    }

    protected ResponseEntity<String> admin(HttpMethod method, String path, Object body) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("X-Admin-Token", ADMIN);
        return rest.exchange(path, method, new HttpEntity<>(body, h), String.class);
    }

    protected JsonNode adminGet(String path) { return json(admin(HttpMethod.GET, path, null)).path("data"); }

    protected JsonNode firstEvaluation(ResponseEntity<String> res) {
        JsonNode evals = json(res).path("data").path("evaluations");
        if (evals.size() != 1) throw new AssertionError("expected 1 evaluation, got " + res.getBody());
        return evals.get(0);
    }
}
