// load/results/<exp>-<mode>-<n>.json (+ .stats.csv) 를 읽어 모드별 중앙값 ± 표준편차 표를 만든다.
// 사용: node load/summarize.js <A|B|C|D|E>
const fs = require('fs');
const path = require('path');
const exp = process.argv[2];
if (!exp) { console.error('usage: node load/summarize.js <A|B|C|D|E>'); process.exit(1); }
const dir = path.join(__dirname, 'results');
const files = fs.readdirSync(dir).filter((f) => f.startsWith(`${exp}-`) && f.endsWith('.json'));
if (!files.length) { console.error(`no results for ${exp} in ${dir}`); process.exit(1); }

function flatten(obj, prefix = '', out = {}) {
  for (const [k, v] of Object.entries(obj || {})) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && !Array.isArray(v)) flatten(v, key, out);
    else if (typeof v === 'number' && Number.isFinite(v)) out[key] = v;
  }
  return out;
}
function cpuFromCsv(csv) {
  if (!fs.existsSync(csv)) return {};
  const rows = fs.readFileSync(csv, 'utf8').trim().split('\n').slice(1).map((l) => l.split(','));
  const out = {};
  for (const name of ['spike-mysql', 'spike-redis']) {
    const v = rows.filter((r) => r[1] === name).map((r) => parseFloat(r[2])).filter(Number.isFinite);
    if (v.length) { out[`docker.${name}.cpu_avg_pct`] = v.reduce((a, b) => a + b, 0) / v.length; out[`docker.${name}.cpu_max_pct`] = Math.max(...v); }
  }
  return out;
}
const median = (a) => { const s = [...a].sort((x, y) => x - y); const m = Math.floor(s.length / 2); return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; };
const stddev = (a) => { const m = a.reduce((x, y) => x + y, 0) / a.length; return Math.sqrt(a.reduce((x, y) => x + (y - m) ** 2, 0) / a.length); };
const fmt = (n) => Math.abs(n) >= 100 ? n.toFixed(0) : Math.abs(n) >= 10 ? n.toFixed(1) : n.toFixed(2);

const byMode = {};
for (const f of files) {
  const j = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
  const m = /^(.+)-(\d+)\.json$/.exec(f.slice(exp.length + 1));
  const mode = j.mode || (m ? m[1] : 'default');
  const flat = { ...flatten({ k6: j.k6, webhook: j.webhook, app: j.app, appDelta: j.appDelta, derived: j.derived, alertStatusDistribution: j.alertStatusDistribution, failedByPhase: j.failedByPhase }), ...cpuFromCsv(path.join(dir, f.replace(/\.json$/, '.stats.csv'))) };
  (byMode[mode] ||= []).push(flat);
}
const modes = Object.keys(byMode).sort();
const keys = [...new Set(modes.flatMap((m) => byMode[m].flatMap(Object.keys)))].filter((k) => !/\.count$|\.min$/.test(k)).sort();
const lines = [`# 실험 ${exp} 요약 (중앙값 ± 표준편차, n=${modes.map((m) => `${m}:${byMode[m].length}`).join(', ')})`, '', `| 지표 | ${modes.join(' | ')} |`, `|---|${modes.map(() => '---:').join('|')}|`];
for (const k of keys) {
  const cells = modes.map((m) => { const v = byMode[m].map((r) => r[k]).filter((x) => x != null); return v.length ? `${fmt(median(v))} ± ${fmt(stddev(v))}` : '—'; });
  lines.push(`| ${k} | ${cells.join(' | ')} |`);
}
const md = lines.join('\n') + '\n';
fs.writeFileSync(path.join(dir, `summary-${exp}.md`), md);
console.log(md);
