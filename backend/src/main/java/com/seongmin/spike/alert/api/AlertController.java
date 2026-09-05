package com.seongmin.spike.alert.api;

import com.seongmin.spike.alert.api.dto.AlertResponse;
import com.seongmin.spike.alert.application.AlertDispatcher;
import com.seongmin.spike.alert.application.CooldownManager;
import com.seongmin.spike.alert.domain.AlertHistory;
import com.seongmin.spike.alert.domain.AlertHistoryRepository;
import com.seongmin.spike.alert.domain.AlertStatus;
import com.seongmin.spike.common.exception.ApiException;
import com.seongmin.spike.common.response.ApiResponse;
import com.seongmin.spike.common.response.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {
    private final AlertHistoryRepository histories;
    private final AlertDispatcher dispatcher;
    private final CooldownManager cooldown;

    @GetMapping
    public ApiResponse<PageResponse<AlertResponse>> list(@RequestParam long projectId,
                                                         @RequestParam(required = false) Long policyId,
                                                         @RequestParam(required = false) AlertStatus status,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "50") int size) {
        var result = histories.search(projectId, policyId, status, PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
        return ApiResponse.ok(PageResponse.of(result, AlertResponse::summary));
    }

    @GetMapping("/cooldowns")
    public ApiResponse<List<CooldownManager.ActiveCooldown>> cooldowns(@RequestParam long projectId) {
        return ApiResponse.ok(cooldown.activeCooldowns(projectId));
    }

    @GetMapping("/{alertId}")
    public ApiResponse<AlertResponse> detail(@PathVariable long alertId) {
        return ApiResponse.ok(AlertResponse.detail(find(alertId)));
    }

    /** FAILED 만 재발송. cooldown 키는 건드리지 않는다. */
    @PostMapping("/{alertId}/retry")
    public ResponseEntity<ApiResponse<AlertResponse>> retry(@PathVariable long alertId) {
        AlertHistory h = find(alertId);
        if (h.getStatus() != AlertStatus.FAILED) throw ApiException.conflict("ALERT_NOT_RETRYABLE", "status is " + h.getStatus());
        h.markPending();
        histories.save(h);
        dispatcher.dispatch(alertId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.ok(AlertResponse.detail(h)));
    }

    private AlertHistory find(long id) {
        return histories.findById(id).orElseThrow(() -> ApiException.notFound("ALERT_NOT_FOUND", "alert " + id));
    }
}
