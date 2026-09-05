package com.seongmin.spike.error.application;

import com.seongmin.spike.error.api.dto.TrendResponse;
import com.seongmin.spike.error.domain.Environment;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 추이·집계 조회. 인덱스 (project_id[, environment[, fingerprint]], received_at) 범위 스캔. */
@Service
@RequiredArgsConstructor
public class ErrorQueryService {
    private final JdbcTemplate jdbc;

    public TrendResponse trend(long projectId, Environment env, String fingerprint, Instant from, Instant to, int stepSeconds) {
        StringBuilder where = new StringBuilder(" WHERE project_id = ? AND received_at >= ? AND received_at < ?");
        List<Object> args = new ArrayList<>(List.of(projectId, utc(from), utc(to)));
        if (env != null) { where.append(" AND environment = ?"); args.add(env.name()); }
        if (fingerprint != null) { where.append(" AND fingerprint = ?"); args.add(fingerprint); }

        List<Object> bucketArgs = new ArrayList<>(List.of(stepSeconds, stepSeconds));
        bucketArgs.addAll(args);
        List<TrendResponse.Bucket> buckets = jdbc.query(
                "SELECT FLOOR(UNIX_TIMESTAMP(received_at) / ?) * ? AS ts, COUNT(*) AS c FROM error_events" + where
                        + " GROUP BY ts ORDER BY ts",
                (rs, i) -> new TrendResponse.Bucket(Instant.ofEpochSecond(rs.getLong("ts")), rs.getLong("c")),
                bucketArgs.toArray());

        Instant now = Instant.now();
        StringBuilder recentWhere = new StringBuilder(" WHERE project_id = ? AND received_at >= ?");
        List<Object> recentArgs = new ArrayList<>(List.of(utc(now.minusSeconds(60)), utc(now.minusSeconds(300)),
                utc(now.minusSeconds(3600)), projectId, utc(now.minusSeconds(86400))));
        if (env != null) { recentWhere.append(" AND environment = ?"); recentArgs.add(env.name()); }
        Map<String, Long> recent = jdbc.queryForObject(
                "SELECT COALESCE(SUM(received_at >= ?),0) m1, COALESCE(SUM(received_at >= ?),0) m5, "
                        + "COALESCE(SUM(received_at >= ?),0) h1, COUNT(*) h24 FROM error_events" + recentWhere,
                (rs, i) -> {
                    Map<String, Long> m = new LinkedHashMap<>();
                    m.put("1m", rs.getLong("m1")); m.put("5m", rs.getLong("m5"));
                    m.put("1h", rs.getLong("h1")); m.put("24h", rs.getLong("h24"));
                    return m;
                }, recentArgs.toArray());

        List<Object> topArgs = new ArrayList<>(List.of(projectId, utc(now.minusSeconds(86400))));
        String topWhere = " WHERE project_id = ? AND received_at >= ?";
        if (env != null) { topWhere += " AND environment = ?"; topArgs.add(env.name()); }
        List<TrendResponse.TopFingerprint> top = jdbc.query(
                "SELECT fingerprint, MAX(error_type) et, COUNT(*) c FROM error_events" + topWhere
                        + " GROUP BY fingerprint ORDER BY c DESC LIMIT 10",
                (rs, i) -> new TrendResponse.TopFingerprint(rs.getString("fingerprint"), rs.getString("et"), rs.getLong("c")),
                topArgs.toArray());
        return new TrendResponse(buckets, recent, top);
    }

    private static LocalDateTime utc(Instant i) { return LocalDateTime.ofInstant(i, ZoneOffset.UTC); }
}
