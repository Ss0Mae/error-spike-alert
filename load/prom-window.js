// 실험 구간(시작·끝 epoch ms)의 Prometheus 히스토그램 분위수·카운터 증가량을 뽑는다. 결과 JSON 의 params.startedAt 과 duration 으로 사후 계산 가능.
// 사용: node load/prom-window.js <startMs> <endMs> [prometheus=http://localhost:9091]
const [,, s, e, base = 'http://localhost:9091'] = process.argv;
const start = Number(s), end = Number(e); const win = Math.max(15, Math.round((end - start) / 1000)) + 's';
const q = {
  'ingestion p50 (ms)': `histogram_quantile(0.5, sum(rate(error_ingestion_duration_seconds_bucket[${win}])) by (le))*1000`,
  'ingestion p95 (ms)': `histogram_quantile(0.95, sum(rate(error_ingestion_duration_seconds_bucket[${win}])) by (le))*1000`,
  'ingestion p99 (ms)': `histogram_quantile(0.99, sum(rate(error_ingestion_duration_seconds_bucket[${win}])) by (le))*1000`,
  'counter REDIS avg (ms)': `sum(rate(error_counter_duration_seconds_sum{path="REDIS"}[${win}]))/sum(rate(error_counter_duration_seconds_count{path="REDIS"}[${win}]))*1000`,
  'counter REDIS p95 (ms)': `histogram_quantile(0.95, sum(rate(error_counter_duration_seconds_bucket{path="REDIS"}[${win}])) by (le))*1000`,
  'counter DB avg (ms)': `sum(rate(error_counter_duration_seconds_sum{path="DB_FALLBACK"}[${win}]))/sum(rate(error_counter_duration_seconds_count{path="DB_FALLBACK"}[${win}]))*1000`,
  'counter DB p95 (ms)': `histogram_quantile(0.95, sum(rate(error_counter_duration_seconds_bucket{path="DB_FALLBACK"}[${win}])) by (le))*1000`,
  'db fallback query avg (ms)': `sum(rate(database_fallback_duration_seconds_sum[${win}]))/sum(rate(database_fallback_duration_seconds_count[${win}]))*1000`,
  'db fallback query p95 (ms)': `histogram_quantile(0.95, sum(rate(database_fallback_duration_seconds_bucket[${win}])) by (le))*1000`,
  'insert≈ingestion−counter avg (ms)': `(sum(rate(error_ingestion_duration_seconds_sum[${win}]))-sum(rate(error_counter_duration_seconds_sum[${win}])))/sum(rate(error_ingestion_duration_seconds_count[${win}]))*1000`,
  'alert send p95 (ms)': `histogram_quantile(0.95, sum(rate(alert_send_duration_seconds_bucket[${win}])) by (le))*1000`,
  'alert send avg (ms)': `sum(rate(alert_send_duration_seconds_sum[${win}]))/sum(rate(alert_send_duration_seconds_count[${win}]))*1000`,
  'detection delay p95 (ms)': `histogram_quantile(0.95, sum(rate(alert_detection_delay_seconds_bucket[${win}])) by (le))*1000`,
  'http /api/errors p95 (ms)': `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{uri="/api/errors"}[${win}])) by (le))*1000`,
  'hikari pending max': `max_over_time(hikaricp_connections_pending[${win}])`,
  'hikari active max': `max_over_time(hikaricp_connections_active[${win}])`,
  'executor queue max': `max_over_time(async_executor_queue_size[${win}])`,
  'executor active max': `max_over_time(async_executor_active_threads[${win}])`,
  'detection_skipped Δ': `sum(increase(detection_skipped_total[${win}]))`,
  'database_fallback Δ': `sum(increase(database_fallback_total[${win}]))`,
  'alert_retry Δ': `sum(increase(alert_retry_total[${win}]))`,
  'errors_received Δ': `sum(increase(errors_received_total[${win}]))`,
};
(async () => {
  const out = {};
  for (const [k, expr] of Object.entries(q)) {
    const r = await fetch(`${base}/api/v1/query?query=${encodeURIComponent(expr)}&time=${end / 1000}`).then((x) => x.json()).catch(() => null);
    const v = r && r.data && r.data.result[0] ? Number(r.data.result[0].value[1]) : null;
    out[k] = v == null || Number.isNaN(v) ? null : Math.round(v * 100) / 100;
  }
  console.log(JSON.stringify(out));
})();
