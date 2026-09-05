// Mock Webhook Server — Node stdlib only. 실험 A/D/E의 수신측 계측기.
// POST /webhook : 알림 수신 (mode에 따라 실패 주입). Idempotency-Key 로 중복 판정.
// POST /control : {mode, failRate, delayMs, retryAfterSec} 부분 갱신. GET /control
// POST /reset   : 기록 초기화.  GET /received?limit=N  GET /stats  GET /metrics  GET /healthz
const http = require('http');

const PORT = Number(process.env.PORT || 8091);
const MODES = ['ok', 'error500', 'rate429', 'timeout', 'slow', 'close'];
let state = { mode: 'ok', failRate: 1, delayMs: 2000, retryAfterSec: 2 };

// records: 모든 도착. successKeys: 2xx 로 처리된 Idempotency-Key 집합.
let records = [];
let successKeys = new Set();
let duplicates = 0;
const byOutcome = Object.fromEntries(MODES.map((m) => [m, 0]));
let pendingTimers = new Set();

function reset() {
  records = [];
  successKeys = new Set();
  duplicates = 0;
  for (const m of MODES) byOutcome[m] = 0;
  for (const t of pendingTimers) clearTimeout(t);
  pendingTimers.clear();
}

function json(res, code, body, headers = {}) {
  const s = JSON.stringify(body);
  res.writeHead(code, { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(s), ...headers });
  res.end(s);
}

function readBody(req) {
  return new Promise((resolve) => {
    let buf = '';
    req.on('data', (c) => (buf += c));
    req.on('end', () => {
      try { resolve(buf ? JSON.parse(buf) : {}); } catch { resolve({ _parseError: true, raw: buf }); }
    });
  });
}

function pct(sorted, p) {
  if (!sorted.length) return null;
  const idx = Math.min(sorted.length - 1, Math.ceil((p / 100) * sorted.length) - 1);
  return sorted[Math.max(0, idx)];
}

function dist(values) {
  const v = values.filter((x) => Number.isFinite(x)).sort((a, b) => a - b);
  if (!v.length) return { count: 0, avg: null, p50: null, p95: null, p99: null, max: null };
  return {
    count: v.length,
    avg: Math.round((v.reduce((a, b) => a + b, 0) / v.length) * 10) / 10,
    p50: pct(v, 50), p95: pct(v, 95), p99: pct(v, 99), max: v[v.length - 1],
  };
}

function stats() {
  const ok = records.filter((r) => r.outcome === 'ok');
  return {
    state,
    total: records.length,
    ok: ok.length,
    failed: records.length - ok.length,
    unique: successKeys.size,
    duplicates,
    byOutcome,
    detectionDelayMs: dist(ok.map((r) => r.detectionDelayMs)),
    dispatchDelayMs: dist(ok.map((r) => r.dispatchDelayMs)),
    firstReceivedAt: records.length ? records[0].receivedAt : null,
    lastReceivedAt: records.length ? records[records.length - 1].receivedAt : null,
  };
}

function metrics() {
  const s = stats();
  const lines = ['# TYPE webhook_received_total counter'];
  for (const m of MODES) lines.push(`webhook_received_total{outcome="${m}"} ${byOutcome[m]}`);
  lines.push('# TYPE webhook_unique_total counter', `webhook_unique_total ${s.unique}`);
  lines.push('# TYPE webhook_duplicates_total counter', `webhook_duplicates_total ${s.duplicates}`);
  lines.push('# TYPE webhook_detection_delay_ms summary');
  const d = s.detectionDelayMs;
  for (const [q, v] of [['0.5', d.p50], ['0.95', d.p95], ['0.99', d.p99]]) {
    lines.push(`webhook_detection_delay_ms{quantile="${q}"} ${v == null ? 'NaN' : v}`);
  }
  lines.push(`webhook_detection_delay_ms_count ${d.count}`);
  return lines.join('\n') + '\n';
}

async function handleWebhook(req, res) {
  const body = await readBody(req);
  const receivedAt = Date.now();
  const key = req.headers['idempotency-key'] || `alert-${body.alertId}`;
  const applyFailure = state.mode !== 'ok' && Math.random() < state.failRate;
  const outcome = applyFailure ? state.mode : 'ok';
  byOutcome[outcome]++;
  const rec = {
    alertId: body.alertId ?? null,
    idempotencyKey: key,
    receivedAt,
    attempt: body.attempt ?? null,
    outcome,
    detectionDelayMs: body.triggerEventReceivedAt ? receivedAt - Date.parse(body.triggerEventReceivedAt) : null,
    dispatchDelayMs: body.detectedAt ? receivedAt - Date.parse(body.detectedAt) : null,
  };
  records.push(rec);
  let dup = false;
  if (outcome === 'ok') {
    if (successKeys.has(key)) { duplicates++; dup = true; } else successKeys.add(key);
  }
  console.log(`[webhook] ${new Date(receivedAt).toISOString()} key=${key} attempt=${rec.attempt} outcome=${outcome}${dup ? ' DUPLICATE' : ''} detectionDelayMs=${rec.detectionDelayMs}`);

  switch (outcome) {
    case 'ok':
      return json(res, 200, { ok: true, duplicate: dup, alertId: rec.alertId });
    case 'error500':
      return json(res, 500, { error: 'injected failure' });
    case 'rate429':
      return json(res, 429, { error: 'rate limited' }, { 'Retry-After': String(state.retryAfterSec) });
    case 'slow': {
      const t = setTimeout(() => { pendingTimers.delete(t); json(res, 200, { ok: true, slow: true, alertId: rec.alertId }); }, state.delayMs);
      pendingTimers.add(t);
      return;
    }
    case 'timeout': {
      // 클라이언트 read timeout(3s)을 확실히 넘기도록 60초 뒤에나 응답
      const t = setTimeout(() => { pendingTimers.delete(t); json(res, 200, { ok: true, late: true }); }, 60_000);
      pendingTimers.add(t);
      return;
    }
    case 'close':
      return req.socket.destroy();
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, 'http://x');
  const p = url.pathname;
  if (req.method === 'POST' && p === '/webhook') return handleWebhook(req, res);
  if (p === '/control') {
    if (req.method === 'POST') {
      const b = await readBody(req);
      if (b.mode !== undefined) {
        if (!MODES.includes(b.mode)) return json(res, 400, { error: `mode must be one of ${MODES.join('|')}` });
        state.mode = b.mode;
      }
      if (b.failRate !== undefined) state.failRate = Math.min(1, Math.max(0, Number(b.failRate)));
      if (b.delayMs !== undefined) state.delayMs = Math.max(0, Number(b.delayMs));
      if (b.retryAfterSec !== undefined) state.retryAfterSec = Math.max(0, Number(b.retryAfterSec));
      console.log('[control]', JSON.stringify(state));
    }
    return json(res, 200, state);
  }
  if (req.method === 'POST' && p === '/reset') { reset(); return json(res, 200, { ok: true }); }
  if (p === '/received') {
    const limit = Number(url.searchParams.get('limit') || 100);
    return json(res, 200, records.slice(-limit));
  }
  if (p === '/stats') return json(res, 200, stats());
  if (p === '/metrics') {
    const m = metrics();
    res.writeHead(200, { 'Content-Type': 'text/plain; version=0.0.4' });
    return res.end(m);
  }
  if (p === '/healthz') return json(res, 200, { ok: true });
  json(res, 404, { error: 'not found' });
});

server.listen(PORT, () => console.log(`mock-webhook listening on :${PORT} state=${JSON.stringify(state)}`));
