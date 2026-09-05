// 실험 E: 알림 서버 장애. FAIL_MODE=error500|rate429|timeout|slow, FAIL_RATE, DELAY_MS, RETRY_AFTER
import { check, sleep } from 'k6';
import { postError, parseData, ensurePolicy, webhookReset, webhookControl, webhookStats, adminGet, appCounters, appStatus, k6Metrics, emit, TREND_STATS, pickFingerprint, eventId } from './lib.js';

const FAIL_MODE = __ENV.FAIL_MODE || 'error500';
const FAIL_RATE = Number(__ENV.FAIL_RATE ?? 0.5);
const DELAY_MS = Number(__ENV.DELAY_MS || 2000);
const RETRY_AFTER = Number(__ENV.RETRY_AFTER || 2);
const DURATION = __ENV.DURATION || '60s';
const FP_POOL = Number(__ENV.FP_POOL || 20);

export const options = {
  summaryTrendStats: TREND_STATS,
  scenarios: {
    flood: { executor: 'constant-arrival-rate', rate: 50, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 30, maxVUs: 150, exec: 'flood', tags: { phase: 'flood' } },
    probe: { executor: 'constant-arrival-rate', rate: 5, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 5, maxVUs: 20, exec: 'probe', tags: { phase: 'probe' } },
  },
  thresholds: { 'http_req_duration{phase:probe}': ['p(95)<10000'], 'http_req_duration{phase:flood}': ['p(95)<10000'] },
};

export function setup() {
  const policyId = ensurePolicy({ windowSeconds: 60, threshold: 5, cooldownSeconds: 0 });
  webhookReset();
  webhookControl({ mode: FAIL_MODE, failRate: FAIL_RATE, delayMs: DELAY_MS, retryAfterSec: RETRY_AFTER });
  const before = appCounters(['alerts_detected_total', 'alerts_sent_total', 'alerts_failed_total', 'alert_retry_total', 'alerts_suppressed_total']);
  return { policyId, failMode: FAIL_MODE, failRate: FAIL_RATE, delayMs: DELAY_MS, retryAfter: RETRY_AFTER, duration: DURATION, countersBefore: before, startedAt: Date.now() };
}

export function flood() {
  const r = postError({ fingerprint: pickFingerprint('k6-e', FP_POOL), eventId: eventId('e'), tags: { phase: 'flood' } });
  check(r, { 'flood accepted': (x) => x.status === 202 });
}

export function probe() {
  const r = postError({ environment: 'DEV', fingerprint: 'k6-e-probe', eventId: eventId('ep'), tags: { phase: 'probe' } });
  check(r, { 'probe accepted': (x) => x.status === 202 });
}

export function teardown() { sleep(15); webhookControl({ mode: 'ok', failRate: 0 }); }

export function handleSummary(data) {
  const s = data.setup_data || {};
  const after = appCounters(['alerts_detected_total', 'alerts_sent_total', 'alerts_failed_total', 'alert_retry_total', 'alerts_suppressed_total', 'async_executor_queue_size']);
  const delta = {};
  for (const k of Object.keys(after)) delta[k] = after[k] != null && s.countersBefore && s.countersBefore[k] != null ? after[k] - s.countersBefore[k] : after[k];
  const wh = webhookStats();
  const statusDist = {};
  let scanned = 0;
  for (let page = 0; page < 10; page++) {
    let body;
    try { body = parseData(adminGet(`/api/alerts?projectId=1&page=${page}&size=200`)); } catch { break; }
    if (!body || !body.content || !body.content.length) break;
    for (const a of body.content) {
      if (s.startedAt && Date.parse(a.detectedAt) < s.startedAt - 1000) continue;
      statusDist[a.status] = (statusDist[a.status] || 0) + 1; scanned++;
    }
    if ((page + 1) * 200 >= body.totalElements) break;
  }
  const detected = delta.alerts_detected_total, sent = delta.alerts_sent_total, failed = delta.alerts_failed_total, retries = delta.alert_retry_total;
  const derived = {
    successRateBeforeRetry: wh && wh.total ? Math.round((wh.ok / wh.total) * 1000) / 10 : null, // webhook 도착 기준 1차 성공률(%)
    finalSuccessRate: detected ? Math.round(((sent || 0) / detected) * 1000) / 10 : null,           // 재시도 포함 최종 성공률(%)
    avgRetriesPerAlert: detected ? Math.round(((retries || 0) / detected) * 100) / 100 : null,
    finalFailed: failed, duplicates: wh ? wh.duplicates : null,
  };
  const result = {
    exp: 'E', mode: `${FAIL_MODE}-${FAIL_RATE}`, params: s, k6: k6Metrics(data), appDelta: delta, derived, webhook: wh && { total: wh.total, ok: wh.ok, failed: wh.failed, unique: wh.unique, duplicates: wh.duplicates, byOutcome: wh.byOutcome, dispatchDelayMs: wh.dispatchDelayMs },
    alertStatusDistribution: statusDist, alertsScanned: scanned, status: appStatus(),
  };
  return emit('E', result, __ENV.OUT_FILE || `load/results/E-${FAIL_MODE}-${FAIL_RATE}.json`);
}
