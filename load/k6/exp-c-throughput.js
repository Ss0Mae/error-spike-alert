// 실험 C: 100 RPS 정상 처리(워밍업 1분 + 측정 5분) 후 200/500/1000 RPS 로 램프해 붕괴 지점을 찾는다.
import { check } from 'k6';
import { postError, ensurePolicy, webhookReset, appCounters, appStatus, k6Metrics, emit, TREND_STATS, STACK, pickFingerprint, eventId } from './lib.js';

const MEASURE = __ENV.MEASURE_DURATION || '5m';
const WARMUP = __ENV.WARMUP_DURATION || '1m';
const RAMP_STAGE = __ENV.RAMP_STAGE_DURATION || '1m';
const SKIP_RAMP = __ENV.SKIP_RAMP === '1';
const FP_POOL = Number(__ENV.FP_POOL || 50);

function secs(s) { const m = /^(\d+)(s|m)$/.exec(s); return m[2] === 'm' ? Number(m[1]) * 60 : Number(m[1]); }

const scenarios = {
  warmup: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s', duration: WARMUP, preAllocatedVUs: 50, maxVUs: 200, tags: { phase: 'warmup' } },
  measure: { executor: 'constant-arrival-rate', rate: 100, timeUnit: '1s', duration: MEASURE, preAllocatedVUs: 50, maxVUs: 200, startTime: `${secs(WARMUP)}s`, tags: { phase: 'measure' } },
};
if (!SKIP_RAMP) {
  const start = secs(WARMUP) + secs(MEASURE);
  scenarios.ramp200 = { executor: 'constant-arrival-rate', rate: 200, timeUnit: '1s', duration: RAMP_STAGE, preAllocatedVUs: 100, maxVUs: 400, startTime: `${start}s`, tags: { phase: 'ramp200' } };
  scenarios.ramp500 = { executor: 'constant-arrival-rate', rate: 500, timeUnit: '1s', duration: RAMP_STAGE, preAllocatedVUs: 200, maxVUs: 800, startTime: `${start + secs(RAMP_STAGE)}s`, tags: { phase: 'ramp500' } };
  scenarios.ramp1000 = { executor: 'constant-arrival-rate', rate: 1000, timeUnit: '1s', duration: RAMP_STAGE, preAllocatedVUs: 400, maxVUs: 1500, startTime: `${start + 2 * secs(RAMP_STAGE)}s`, tags: { phase: 'ramp1000' } };
}

export const options = {
  summaryTrendStats: TREND_STATS,
  scenarios,
  thresholds: {
    'http_req_duration{phase:measure}': ['p(95)<200'],
    'http_req_duration{phase:warmup}': ['p(95)<10000'],
    'http_req_failed{phase:measure}': ['rate<0.01'],
    ...(SKIP_RAMP ? {} : {
      'http_req_duration{phase:ramp200}': ['p(95)<10000'], 'http_req_duration{phase:ramp500}': ['p(95)<10000'], 'http_req_duration{phase:ramp1000}': ['p(95)<10000'],
      'http_req_failed{phase:ramp200}': ['rate<1'], 'http_req_failed{phase:ramp500}': ['rate<1'], 'http_req_failed{phase:ramp1000}': ['rate<1'],
    }),
  },
};

export function setup() {
  const policyId = ensurePolicy({ windowSeconds: 60, threshold: 1000000, cooldownSeconds: 30 });
  webhookReset();
  return { policyId, warmup: WARMUP, measure: MEASURE, skipRamp: SKIP_RAMP, fpPool: FP_POOL };
}

export default function () {
  const r = postError({ fingerprint: pickFingerprint('k6-c', FP_POOL), stackTrace: STACK, eventId: eventId('c') });
  check(r, { 'accepted': (x) => x.status === 202 });
}

export function handleSummary(data) {
  const app = appCounters(['hikaricp_connections_pending', 'hikaricp_connections_active', 'async_executor_queue_size', 'database_fallback_total', 'detection_skipped_total']);
  const m = data.metrics || {};
  const failedByPhase = {};
  for (const k of Object.keys(m)) if (k.startsWith('http_req_failed{')) failedByPhase[k] = m[k].values.rate;
  const result = { exp: 'C', mode: 'redis', params: data.setup_data, k6: k6Metrics(data), failedByPhase, app, status: appStatus() };
  return emit('C', result);
}
