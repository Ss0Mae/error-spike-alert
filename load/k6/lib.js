// 공용 헬퍼. 모든 실험 스크립트가 import 한다.
import http from 'k6/http';
import { check } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://localhost:8090';
export const API_KEY = __ENV.API_KEY || 'demo-api-key';
export const ADMIN = __ENV.ADMIN_TOKEN || 'admin-token';
export const WEBHOOK = __ENV.WEBHOOK_URL || 'http://localhost:8091';
// 앱(호스트 JVM)이 webhook 을 부를 때 쓰는 URL. 앱이 컨테이너면 http://mock-webhook:8091/webhook
export const WEBHOOK_URL_FOR_APP = __ENV.WEBHOOK_URL_FOR_APP || 'http://localhost:8091/webhook';
export const PROJECT_ID = Number(__ENV.PROJECT_ID || 1);
export const RUN_ID = __ENV.RUN_ID || Date.now().toString(36);

export const TREND_STATS = ['avg', 'min', 'med', 'max', 'p(50)', 'p(95)', 'p(99)'];

const jsonHeaders = (extra) => ({ headers: { 'Content-Type': 'application/json', ...extra } });
const adminOpts = (tags) => ({ headers: { 'Content-Type': 'application/json', 'X-Admin-Token': ADMIN }, tags: tags || { phase: 'admin' } });

export const STACK = 'java.lang.IllegalStateException: injected by k6\n' +
  Array.from({ length: 25 }, (_, i) => `\tat com.example.order.OrderService.place(OrderService.java:${100 + i})`).join('\n');

export function postError({ environment = 'PRODUCTION', errorType = 'java.lang.IllegalStateException', fingerprint, message = 'k6 injected error', stackTrace, eventId, requestId, tags } = {}) {
  const body = { environment, errorType, message, fingerprint, stackTrace, eventId, requestId, occurredAt: new Date().toISOString() };
  const opts = jsonHeaders({ 'X-API-Key': API_KEY });
  if (tags) opts.tags = tags;
  return http.post(`${BASE}/api/errors`, JSON.stringify(body), opts);
}

export function parseData(res) {
  try { return res.json('data'); } catch { return null; }
}

export function adminGet(path, tags) { return http.get(`${BASE}${path}`, adminOpts(tags)); }
export function adminPost(path, body, tags) { return http.post(`${BASE}${path}`, JSON.stringify(body || {}), adminOpts(tags)); }
export function adminPatch(path, body, tags) { return http.patch(`${BASE}${path}`, JSON.stringify(body || {}), adminOpts(tags)); }

// 프로젝트 1 의 정책 1 을 원하는 값으로 맞춘다. 없으면 생성.
export function ensurePolicy({ windowSeconds = 60, threshold = 20, cooldownSeconds = 30, scope = 'PER_FINGERPRINT', environment = 'PRODUCTION' } = {}) {
  const fields = { environment, scope, targetFingerprint: null, windowSeconds, threshold, cooldownSeconds, channel: 'WEBHOOK', webhookUrl: WEBHOOK_URL_FOR_APP, enabled: true };
  const list = adminGet(`/api/alert-policies?projectId=${PROJECT_ID}`);
  check(list, { 'policy list 200': (r) => r.status === 200 });
  const policies = parseData(list) || [];
  const existing = policies.find((p) => p.id === 1) || policies[0];
  let res;
  if (existing) res = adminPatch(`/api/alert-policies/${existing.id}`, fields);
  else res = adminPost('/api/alert-policies', { projectId: PROJECT_ID, ...fields });
  check(res, { 'policy ensured': (r) => r.status === 200 || r.status === 201 });
  const p = parseData(res);
  return p ? p.id : (existing ? existing.id : 1);
}

export function setBreaker(forceOpen) {
  const r = adminPost('/api/system/redis-breaker', { forceOpen: !!forceOpen });
  check(r, { 'breaker set': (x) => x.status === 200 });
  return parseData(r);
}

export function webhookControl(state) { return http.post(`${WEBHOOK}/control`, JSON.stringify(state), jsonHeaders()); }
export function webhookReset() { return http.post(`${WEBHOOK}/reset`, '{}', jsonHeaders()); }
export function webhookStats() { try { return http.get(`${WEBHOOK}/stats`).json(); } catch { return null; } }

// /actuator/prometheus 텍스트에서 이름이 일치하는 시계열을 라벨 무관하게 합산
export function appCounters(names) {
  const out = {};
  let text = '';
  try { text = http.get(`${BASE}/actuator/prometheus`).body || ''; } catch { return out; }
  for (const name of names) {
    const re = new RegExp(`^${name}(\\{[^}]*\\})? (\\S+)$`, 'gm');
    let m, sum = 0, found = false;
    while ((m = re.exec(text)) !== null) { sum += Number(m[2]); found = true; }
    out[name] = found ? sum : null;
  }
  return out;
}

export function appStatus() { try { return parseData(adminGet('/api/system/status')); } catch { return null; } }

// k6 summary data → 핵심 지표만 추출
export function k6Metrics(data, extraTrendNames = []) {
  const m = data.metrics || {};
  const trend = (name) => {
    const v = (m[name] || {}).values || {};
    return { avg: v.avg, p50: v['p(50)'] ?? v.med, p95: v['p(95)'], p99: v['p(99)'], max: v.max, count: v.count };
  };
  const out = {
    durationMs: trend('http_req_duration'),
    requests: (m.http_reqs || {}).values?.count ?? null,
    rps: (m.http_reqs || {}).values?.rate ?? null,
    failedRate: (m.http_req_failed || {}).values?.rate ?? null,
    droppedIterations: (m.dropped_iterations || {}).values?.count ?? null,
  };
  for (const name of Object.keys(m)) {
    if (name.startsWith('http_req_duration{')) out['durationMs' + name.slice('http_req_duration'.length)] = trend(name);
  }
  for (const t of extraTrendNames) if (m[t]) out[t] = m[t].values;
  return out;
}

export function emit(exp, result, outFile) {
  const line = 'EXPERIMENT_RESULT ' + JSON.stringify(result);
  const files = { stdout: line + '\n' };
  files[outFile || __ENV.OUT_FILE || `load/results/${exp}.json`] = JSON.stringify(result, null, 2);
  return files;
}

export function pickFingerprint(prefix, pool) { return `${prefix}-${Math.floor(Math.random() * pool)}`; }
export function eventId(prefix) { return `${prefix}-${RUN_ID}-${__VU}-${__ITER}`; }
