package com.seongmin.spike.error.api;

import com.seongmin.spike.common.config.AuthInterceptor;
import com.seongmin.spike.common.exception.ApiException;
import com.seongmin.spike.common.response.ApiResponse;
import com.seongmin.spike.common.response.PageResponse;
import com.seongmin.spike.error.api.dto.ErrorDetail;
import com.seongmin.spike.error.api.dto.ErrorSummary;
import com.seongmin.spike.error.api.dto.IngestRequest;
import com.seongmin.spike.error.api.dto.IngestResponse;
import com.seongmin.spike.error.api.dto.TrendResponse;
import com.seongmin.spike.error.application.ErrorIngestionService;
import com.seongmin.spike.error.application.ErrorQueryService;
import com.seongmin.spike.error.domain.Environment;
import com.seongmin.spike.error.domain.ErrorEventRepository;
import com.seongmin.spike.project.domain.Project;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/errors")
@RequiredArgsConstructor
public class ErrorController {
    private final ErrorIngestionService ingestion;
    private final ErrorQueryService query;
    private final ErrorEventRepository events;

    @PostMapping
    public ResponseEntity<ApiResponse<IngestResponse>> ingest(@RequestAttribute(AuthInterceptor.PROJECT_ATTR) Project project,
                                                              @Valid @RequestBody IngestRequest req) {
        IngestResponse res = ingestion.ingest(project, req);
        return ResponseEntity.status(res.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(ApiResponse.ok(res));
    }

    @GetMapping
    public ApiResponse<PageResponse<ErrorSummary>> list(@RequestParam long projectId,
                                                        @RequestParam(required = false) Environment environment,
                                                        @RequestParam(required = false) String fingerprint,
                                                        @RequestParam(required = false) String requestId,
                                                        @RequestParam(required = false) Instant from,
                                                        @RequestParam(required = false) Instant to,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "50") int size) {
        Instant now = Instant.now();
        var result = events.search(projectId, environment, fingerprint, requestId,
                from != null ? from : now.minusSeconds(7 * 86400), to != null ? to : now.plusSeconds(60),
                PageRequest.of(page, Math.min(Math.max(size, 1), 200)));
        return ApiResponse.ok(PageResponse.of(result, ErrorSummary::from));
    }

    @GetMapping("/trend")
    public ApiResponse<TrendResponse> trend(@RequestParam long projectId,
                                            @RequestParam(required = false) Environment environment,
                                            @RequestParam(required = false) String fingerprint,
                                            @RequestParam(required = false) Instant from,
                                            @RequestParam(required = false) Instant to,
                                            @RequestParam(defaultValue = "1m") String interval) {
        int step = switch (interval) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "1h" -> 3600;
            default -> throw ApiException.badRequest("interval must be 1m, 5m or 1h");
        };
        Instant now = Instant.now();
        return ApiResponse.ok(query.trend(projectId, environment, fingerprint,
                from != null ? from : now.minusSeconds(3600), to != null ? to : now.plusSeconds(1), step));
    }

    @GetMapping("/{errorId}")
    public ApiResponse<ErrorDetail> detail(@PathVariable long errorId) {
        return ApiResponse.ok(events.findById(errorId).map(ErrorDetail::from)
                .orElseThrow(() -> ApiException.notFound("ERROR_NOT_FOUND", "error " + errorId)));
    }
}
