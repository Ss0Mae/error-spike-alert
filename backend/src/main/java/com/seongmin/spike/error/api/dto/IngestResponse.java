package com.seongmin.spike.error.api.dto;

import com.seongmin.spike.alert.application.Evaluation;
import java.time.Instant;
import java.util.List;

public record IngestResponse(Long errorId, String eventId, String fingerprint, boolean duplicate, Instant receivedAt,
                             List<Evaluation> evaluations) {}
