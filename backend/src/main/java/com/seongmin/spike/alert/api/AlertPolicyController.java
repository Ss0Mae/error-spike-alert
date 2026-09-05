package com.seongmin.spike.alert.api;

import com.seongmin.spike.alert.api.dto.PolicyPatchRequest;
import com.seongmin.spike.alert.api.dto.PolicyRequest;
import com.seongmin.spike.alert.api.dto.PolicyResponse;
import com.seongmin.spike.alert.application.PolicyCache;
import com.seongmin.spike.alert.domain.AlertPolicy;
import com.seongmin.spike.alert.domain.AlertPolicyRepository;
import com.seongmin.spike.common.exception.ApiException;
import com.seongmin.spike.common.response.ApiResponse;
import com.seongmin.spike.project.domain.ProjectRepository;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alert-policies")
@RequiredArgsConstructor
public class AlertPolicyController {
    private final AlertPolicyRepository policies;
    private final ProjectRepository projects;
    private final PolicyCache cache;

    @PostMapping
    public ResponseEntity<ApiResponse<PolicyResponse>> create(@Valid @RequestBody PolicyRequest req) {
        if (!projects.existsById(req.projectId())) throw ApiException.notFound("PROJECT_NOT_FOUND", "project " + req.projectId());
        Instant now = Instant.now();
        AlertPolicy p = policies.save(AlertPolicy.builder()
                .projectId(req.projectId()).environment(req.environment()).scope(req.scope())
                .targetFingerprint(req.targetFingerprint()).windowSeconds(req.windowSeconds()).threshold(req.threshold())
                .cooldownSeconds(req.cooldownSeconds()).channel(req.channel()).webhookUrl(req.webhookUrl())
                .enabled(req.enabled() == null || req.enabled()).createdAt(now).updatedAt(now).build());
        cache.refreshNow();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(PolicyResponse.from(p)));
    }

    @GetMapping
    public ApiResponse<List<PolicyResponse>> list(@RequestParam long projectId) {
        return ApiResponse.ok(policies.findByProjectIdOrderByIdAsc(projectId).stream().map(PolicyResponse::from).toList());
    }

    @PatchMapping("/{policyId}")
    public ApiResponse<PolicyResponse> patch(@PathVariable long policyId, @Valid @RequestBody PolicyPatchRequest req) {
        AlertPolicy p = find(policyId);
        if (req.environment() != null) p.setEnvironment(req.environment());
        if (req.scope() != null) p.setScope(req.scope());
        if (req.targetFingerprint() != null) p.setTargetFingerprint(req.targetFingerprint().isBlank() ? null : req.targetFingerprint());
        if (req.windowSeconds() != null) p.setWindowSeconds(req.windowSeconds());
        if (req.threshold() != null) p.setThreshold(req.threshold());
        if (req.cooldownSeconds() != null) p.setCooldownSeconds(req.cooldownSeconds());
        if (req.channel() != null) p.setChannel(req.channel());
        if (req.webhookUrl() != null) p.setWebhookUrl(req.webhookUrl());
        if (req.enabled() != null) p.setEnabled(req.enabled());
        p.setUpdatedAt(Instant.now());
        AlertPolicy saved = policies.save(p);
        cache.refreshNow();
        return ApiResponse.ok(PolicyResponse.from(saved));
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(@PathVariable long policyId) {
        AlertPolicy p = find(policyId);
        // ponytail: 이력(FK)이 있으면 물리 삭제가 막히므로 비활성화로 대체. 진짜 삭제가 필요하면 이력도 같이 지운다.
        p.setEnabled(false);
        p.setUpdatedAt(Instant.now());
        policies.save(p);
        cache.refreshNow();
        return ResponseEntity.noContent().build();
    }

    private AlertPolicy find(long id) {
        return policies.findById(id).orElseThrow(() -> ApiException.notFound("POLICY_NOT_FOUND", "policy " + id));
    }
}
