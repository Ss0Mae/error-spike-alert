#!/usr/bin/env python3
"""probe.csv + 이벤트 시각으로 장애 감지·전환·복구 시간, 오류율, 유실을 계산한다.
사용: analyze.py <probe.csv> <T_fault_ms> <T_recover_ms> [db_rows]"""
import csv, sys, json
rows = list(csv.DictReader(open(sys.argv[1])))
t_fault, t_rec = int(sys.argv[2]), int(sys.argv[3])
db_rows = int(sys.argv[4]) if len(sys.argv) > 4 else None
ok = [r for r in rows if r['http'] in ('200', '202')]
first_fb = next((int(r['ts_ms']) for r in rows if int(r['ts_ms']) >= t_fault and r['path'] == 'DB_FALLBACK'), None)
first_redis_after = next((int(r['ts_ms']) for r in rows if int(r['ts_ms']) >= t_rec and r['path'] == 'REDIS'), None)
errs = [r for r in rows if r['http'] not in ('200', '202')]
first_err = next((int(r['ts_ms']) for r in rows if int(r['ts_ms']) >= t_fault and r['http'] not in ('200','202')), None)
last_err = max((int(r['ts_ms']) for r in errs), default=None)
skipped = sum(1 for r in rows if r['result'] == 'SKIPPED')
lat = sorted(int(r['latency_ms']) for r in rows)
p = lambda q: lat[min(len(lat)-1, int(len(lat)*q))] if lat else None
out = {
  'requests': len(rows), 'accepted_2xx': len(ok), 'errors': len(errs), 'error_rate_pct': round(100*len(errs)/max(1,len(rows)),2),
  'first_error_after_fault_ms': None if first_err is None else first_err - t_fault,
  'error_window_ms': None if (first_err is None or last_err is None) else last_err - first_err,
  'fallback_switch_ms_after_fault': None if first_fb is None else first_fb - t_fault,
  'redis_recovery_ms_after_restore': None if first_redis_after is None else first_redis_after - t_rec,
  'detection_skipped': skipped, 'paths': {k: sum(1 for r in rows if r['path']==k) for k in ('REDIS','DB_FALLBACK','NONE')},
  'latency_p50_ms': p(0.5), 'latency_p95_ms': p(0.95), 'latency_max_ms': lat[-1] if lat else None,
  'db_rows': db_rows, 'lost_events': None if db_rows is None else len(ok) - db_rows,
}
print(json.dumps(out, ensure_ascii=False))
