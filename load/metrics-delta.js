// /actuator/prometheus 스냅샷 두 개의 차이로 구간 내 히스토그램 분위수·평균·카운터 증가량을 계산한다 (Prometheus 스크레이프 간격과 무관하게 정확).
// 사용: node load/metrics-delta.js before.prom after.prom
const fs = require('fs');
const parse = (f) => { const m = {}; for (const l of fs.readFileSync(f, 'utf8').split('\n')) { if (!l || l[0] === '#') continue; const i = l.lastIndexOf(' '); m[l.slice(0, i)] = Number(l.slice(i + 1)); } return m; };
const a = parse(process.argv[2]), b = parse(process.argv[3]);
const d = {}; for (const k of Object.keys(b)) d[k] = b[k] - (a[k] || 0);
const hist = (name) => {
  const buckets = Object.keys(d).filter((k) => k.startsWith(name + '_bucket{')).map((k) => ({ le: Number(/le="([^"]+)"/.exec(k)[1]), v: d[k] })).sort((x, y) => x.le - y.le);
  const cnt = Object.keys(d).filter((k) => k.startsWith(name + '_count')).reduce((s, k) => s + d[k], 0);
  const sum = Object.keys(d).filter((k) => k.startsWith(name + '_sum')).reduce((s, k) => s + d[k], 0);
  if (!cnt) return null;
  const q = (p) => { const t = cnt * p; let prev = 0; for (const bk of buckets) { if (bk.v >= t) { const lo = prev, hi = bk.le, f = (t - (bk.prevV || 0)) / Math.max(1, bk.v - (bk.prevV || 0)); return +(lo + (hi - lo) * f).toFixed(4); } prev = bk.le; } return buckets.length ? buckets[buckets.length - 1].le : null; };
  for (let i = 1; i < buckets.length; i++) buckets[i].prevV = buckets[i - 1].v;
  return { count: cnt, avg_ms: +((sum / cnt) * 1000).toFixed(2), p50_ms: +(q(0.5) * 1000).toFixed(2), p95_ms: +(q(0.95) * 1000).toFixed(2), p99_ms: +(q(0.99) * 1000).toFixed(2) };
};
const out = {};
for (const n of ['error_ingestion_duration_seconds', 'error_counter_duration_seconds', 'alert_send_duration_seconds', 'alert_detection_delay_seconds', 'database_fallback_duration_seconds']) out[n] = hist(n);
for (const n of ['errors_received_total', 'alerts_detected_total', 'alerts_sent_total', 'alerts_failed_total', 'alerts_suppressed_total', 'alert_retry_total', 'cooldown_contention_total']) out[n] = Object.keys(d).filter((k) => k === n || k.startsWith(n + '{')).reduce((s, k) => s + d[k], 0);
console.log(JSON.stringify(out, null, 1));
