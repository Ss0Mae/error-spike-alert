package com.seongmin.spike.alert.application;

import com.seongmin.spike.alert.domain.AlertPolicy;
import com.seongmin.spike.alert.domain.AlertPolicyRepository;
import com.seongmin.spike.error.domain.Environment;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** enabled 정책을 프로젝트별로 메모리에 들고 주기적으로 새로 읽는다(다중 인스턴스 안전, 반영 지연 ≤ refresh 주기). */
@Component
@RequiredArgsConstructor
public class PolicyCache {
    private final AlertPolicyRepository policies;
    private volatile Map<Long, List<AlertPolicy>> byProject = Map.of();

    @Scheduled(fixedDelayString = "${spike.policy-cache-refresh-ms}")
    public void refreshNow() {
        byProject = policies.findByEnabledTrue().stream().collect(Collectors.groupingBy(AlertPolicy::getProjectId));
    }

    public List<AlertPolicy> matching(long projectId, Environment env, String fingerprint) {
        return byProject.getOrDefault(projectId, List.of()).stream().filter(p -> p.matches(env, fingerprint)).toList();
    }
}
