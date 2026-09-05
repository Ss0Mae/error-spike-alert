// 실험 D: cooldown 효과. 단일 fingerprint 100 RPS 60초. COOLDOWN=0 (없음) vs 30.
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { postError, parseData, ensurePolicy, webhookReset, webhookControl, webhookStats, adminGet, appCounters, k6Metrics, emit, TREND_STATS, RUN_ID, eventId } from './lib.js';

const COOLDOWN = Number(__ENV.COOLDOWN ?? 30);
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '60s';
const resTriggered = new Counter('res_triggered');
const resSuppressed = new Counter('res_suppressed');
const resNotTriggered = new Counter('res_not_triggered');
const resSkipped = new Counter('res_skipped');

export const options = {
  summaryTrendStats: TREND_STATS,
  scenarios: { flood: { executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION, preAllocatedVUs: 50, maxVUs: 200 } },
};

export function setup() {
  const policyId = ensurePolicy({ windowSeconds: 60, threshold: 20, cooldownSeconds: COOLDOWN });
  webhookReset();
  webhookControl({ mode: 'ok', failRate: 0 });
  const before = appCounters(['alerts_detected_total', 'alerts_suppressed_total', 'cooldown_contention_total', 'alerts_sent_total']);
  return { policyId, cooldown: COOLDOWN, rate: RATE, duration: DURATION, fingerprint: `k6-d-${RUN_ID}`, countersBefore: before, startedAt: Date.now() };
}

export default function (s) {
  const r = postError({ fingerprint: s.fingerprint, eventId: eventId('d') });
  check(r, { 'accepted': (x) => x.status === 202 });
  const d = parseData(r);
  const ev = d && d.evaluations && d.evaluations[0];
  const res = ev ? ev.result : 'NONE';
  if (res === 'TRIGGERED') resTriggered.add(1);
  else if (res === 'SUPPRESSED') resSuppressed.add(1);
  else if (res === 'SKIPPED') resSkipped.add(1);
  else resNotTriggered.add(1);
}

export function teardown() { sleep(3); }

export function handleSummary(data) {
  const s = data.setup_data || {};
  const after = appCounters(['alerts_detected_total', 'alerts_suppressed_total', 'cooldown_contention_total', 'alerts_sent_total']);
  const delta = {};
  for (const k of Object.keys(after)) delta[k] = after[k] != null && s.countersBefore && s.countersBefore[k] != null ? after[k] - s.countersBefore[k] : after[k];
  const wh = webhookStats();
  let alerts = [];
  try {
    const list = parseData(adminGet(`/api/alerts?projectId=1&size=20`));
    alerts = (list.content || []).filter((a) => a.fingerprint === s.fingerprint).map((a) => ({ id: a.id, detectedAt: a.detectedAt, status: a.status, count: a.detectedCount, offsetSec: s.startedAt ? Math.round((Date.parse(a.detectedAt) - s.startedAt) / 100) / 10 : null }));
  } catch {}
  const result = {
    exp: 'D', mode: `cooldown-${COOLDOWN}`, params: s, k6: k6Metrics(data, ['res_triggered', 'res_suppressed', 'res_not_triggered', 'res_skipped']),
    appDelta: delta, webhook: wh && { unique: wh.unique, duplicates: wh.duplicates, ok: wh.ok, total: wh.total, detectionDelayMs: wh.detectionDelayMs },
    alertsForFingerprint: alerts,
  };
  return emit('D', result, __ENV.OUT_FILE || `load/results/D-cooldown-${COOLDOWN}.json`);
}
