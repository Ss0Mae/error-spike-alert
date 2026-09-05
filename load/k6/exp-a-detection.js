// 실험 A: 감지 지연. 새 fingerprint 로 20건을 순차 전송 → 20번째에서 TRIGGERED → webhook 도착까지.
import { check } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { postError, parseData, ensurePolicy, webhookReset, webhookControl, webhookStats, appCounters, k6Metrics, emit, TREND_STATS, RUN_ID, eventId } from './lib.js';

const VUS = Number(__ENV.VUS || 3);
const ITERS = Number(__ENV.ITERS || 10);
const N = Number(__ENV.THRESHOLD || 20);
const api20th = new Trend('api_20th_latency', true);
const triggered = new Counter('threshold_triggered');
const notTriggered = new Counter('threshold_not_triggered');

export const options = {
  summaryTrendStats: TREND_STATS,
  scenarios: { burst: { executor: 'per-vu-iterations', vus: VUS, iterations: ITERS, maxDuration: '10m' } },
};

export function setup() {
  const policyId = ensurePolicy({ windowSeconds: 60, threshold: N, cooldownSeconds: 30 });
  webhookReset();
  webhookControl({ mode: 'ok', failRate: 0 });
  return { policyId, runId: RUN_ID, threshold: N, vus: VUS, iters: ITERS };
}

export default function () {
  const fp = `k6-a-${RUN_ID}-${__VU}-${__ITER}`;
  let last;
  for (let i = 1; i <= N; i++) {
    last = postError({ fingerprint: fp, eventId: `${eventId('a')}-${i}`, tags: { phase: i === N ? 'nth' : 'fill' } });
  }
  api20th.add(last.timings.duration);
  const d = parseData(last);
  const ok = check(last, { 'nth accepted': (r) => r.status === 202 });
  const ev = d && d.evaluations && d.evaluations[0];
  if (ok && ev && ev.result === 'TRIGGERED') triggered.add(1); else notTriggered.add(1);
}

export function handleSummary(data) {
  const wh = webhookStats();
  const app = appCounters(['alerts_detected_total', 'alerts_sent_total', 'alerts_failed_total', 'alerts_suppressed_total']);
  const result = {
    exp: 'A', mode: 'redis', params: data.setup_data, k6: k6Metrics(data, ['api_20th_latency', 'threshold_triggered', 'threshold_not_triggered']),
    webhook: wh && { unique: wh.unique, duplicates: wh.duplicates, detectionDelayMs: wh.detectionDelayMs, dispatchDelayMs: wh.dispatchDelayMs },
    app,
  };
  return emit('A', result);
}
