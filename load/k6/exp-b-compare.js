// 실험 B: Redis 경로 vs DB fallback(인덱스 有/無) 비교. MODE=redis|db-index|db-noindex
import { check } from 'k6';
import { Trend } from 'k6/metrics';
import { postError, parseData, ensurePolicy, setBreaker, webhookReset, appCounters, appStatus, k6Metrics, emit, TREND_STATS, STACK, pickFingerprint, eventId } from './lib.js';

const MODE = __ENV.MODE || 'redis';
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '3m';
const FP_POOL = Number(__ENV.FP_POOL || 50);
const pathDb = new Trend('counter_path_db');

export const options = {
  summaryTrendStats: TREND_STATS,
  scenarios: { load: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 50, maxVUs: 200 } },
  thresholds: { http_req_failed: ['rate<0.01'] },
};

export function setup() {
  const policyId = ensurePolicy({ windowSeconds: 60, threshold: 1000000, cooldownSeconds: 30 });
  webhookReset();
  const breaker = setBreaker(MODE !== 'redis');
  return { mode: MODE, rate: RATE, duration: DURATION, fpPool: FP_POOL, policyId, breakerAtStart: breaker };
}

export default function () {
  const r = postError({ fingerprint: pickFingerprint('k6-b', FP_POOL), stackTrace: STACK, eventId: eventId('b') });
  check(r, { 'accepted': (x) => x.status === 202 });
  const d = parseData(r);
  const ev = d && d.evaluations && d.evaluations[0];
  pathDb.add(ev && ev.path === 'DB_FALLBACK' ? 1 : 0);
}

export function teardown() { setBreaker(false); }

export function handleSummary(data) {
  const app = appCounters(['database_fallback_total', 'detection_skipped_total', 'redis_counter_failure_total', 'hikaricp_connections_pending', 'hikaricp_connections_active']);
  const result = { exp: 'B', mode: MODE, params: data.setup_data, k6: k6Metrics(data, ['counter_path_db']), app, status: appStatus() };
  return emit('B', result, __ENV.OUT_FILE || `load/results/B-${MODE}.json`);
}
