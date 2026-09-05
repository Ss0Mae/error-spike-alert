import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRef, useState } from 'react'
import { useApp } from '../App'
import { ENVS, api, type Env, type IngestResult } from '../api'
import { Empty, ErrorNote, Field, Panel, Status, fmtTime, modeLabel, modeTone, num, pathLabel, resultLabel, resultTone } from '../ui'

interface LogRow {
  i: number
  at: number
  result?: IngestResult
  error?: string
}

const SAMPLE_TRACE = `java.lang.NullPointerException: Cannot invoke "String.length()" because "s" is null
\tat com.example.order.OrderService.validate(OrderService.java:42)
\tat com.example.order.OrderController.create(OrderController.java:31)
\tat jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)`

export default function FirePage() {
  const { project } = useApp()
  const qc = useQueryClient()
  const [environment, setEnvironment] = useState<Env>('PRODUCTION')
  const [errorType, setErrorType] = useState('java.lang.NullPointerException')
  const [message, setMessage] = useState('Cannot invoke "String.length()" because "s" is null')
  const [fingerprint, setFingerprint] = useState('')
  const [count, setCount] = useState(25)
  const [intervalMs, setIntervalMs] = useState(50)
  const [withTrace, setWithTrace] = useState(true)
  const [running, setRunning] = useState(false)
  const [log, setLog] = useState<LogRow[]>([])
  const stop = useRef(false)

  const statusQ = useQuery({ queryKey: ['status'], queryFn: api.status, refetchInterval: 5_000 })
  const breaker = useMutation({
    mutationFn: (forceOpen: boolean) => api.setBreaker(forceOpen),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['status'] }),
  })

  if (!project) return <Empty>프로젝트를 불러오는 중입니다.</Empty>

  const send = async () => {
    setRunning(true)
    stop.current = false
    setLog([])
    const fp = fingerprint.trim() || undefined
    for (let i = 1; i <= count && !stop.current; i++) {
      const at = Date.now()
      try {
        const result = await api.ingest(project.apiKey, {
          environment,
          errorType,
          message,
          fingerprint: fp,
          stackTrace: withTrace ? SAMPLE_TRACE : undefined,
          serverInstance: 'console',
          requestId: `console-${at}-${i}`,
          metadata: { source: 'spike-console', seq: i },
        })
        setLog((l) => [{ i, at, result }, ...l].slice(0, 200))
      } catch (e) {
        setLog((l) => [{ i, at, error: (e as Error).message }, ...l].slice(0, 200))
      }
      if (intervalMs > 0) await new Promise((r) => setTimeout(r, intervalMs))
    }
    setRunning(false)
    qc.invalidateQueries({ queryKey: ['trend'] })
    qc.invalidateQueries({ queryKey: ['alerts'] })
    qc.invalidateQueries({ queryKey: ['cooldowns'] })
  }

  const triggered = log.filter((r) => r.result?.evaluations.some((e) => e.result === 'TRIGGERED')).length
  const suppressed = log.filter((r) => r.result?.evaluations.some((e) => e.result === 'SUPPRESSED')).length
  const st = statusQ.data

  return (
    <div className="grid gap-4 lg:grid-cols-[2fr_3fr]">
      <div className="grid gap-4 content-start">
        <Panel title="테스트 이벤트 보내기" aside={`API 키 ${project.apiKey}`}>
          <div className="grid gap-3 px-4 pb-4 sm:grid-cols-2">
            <Field label="환경">
              <select className="input w-full" value={environment} onChange={(e) => setEnvironment(e.target.value as Env)}>
                {ENVS.map((e) => (
                  <option key={e}>{e}</option>
                ))}
              </select>
            </Field>
            <Field label="fingerprint (비우면 서버가 만든다)">
              <input className="input w-full font-mono text-[12px]" value={fingerprint} onChange={(e) => setFingerprint(e.target.value)} placeholder="자동" />
            </Field>
            <Field label="에러 종류" className="sm:col-span-2">
              <input className="input w-full" value={errorType} onChange={(e) => setErrorType(e.target.value)} />
            </Field>
            <Field label="메시지" className="sm:col-span-2">
              <input className="input w-full" value={message} onChange={(e) => setMessage(e.target.value)} />
            </Field>
            <Field label="건수">
              <input className="input w-full" type="number" min={1} max={5000} value={count} onChange={(e) => setCount(Number(e.target.value))} />
            </Field>
            <Field label="간격 (ms)">
              <input className="input w-full" type="number" min={0} max={10000} value={intervalMs} onChange={(e) => setIntervalMs(Number(e.target.value))} />
            </Field>
            <label className="flex items-center gap-2 text-[13px] sm:col-span-2">
              <input type="checkbox" checked={withTrace} onChange={(e) => setWithTrace(e.target.checked)} />
              샘플 stack trace 포함
            </label>
            <div className="flex gap-2 sm:col-span-2">
              {running ? (
                <button className="btn btn-danger" onClick={() => (stop.current = true)}>
                  중단
                </button>
              ) : (
                <button className="btn btn-primary" onClick={send}>
                  {num(count)}건 보내기
                </button>
              )}
            </div>
          </div>
        </Panel>

        <Panel title="Redis fallback 강제">
          <ErrorNote error={statusQ.error ?? breaker.error} />
          <div className="grid gap-3 px-4 pb-4 text-[13px]">
            <p className="text-ink2">
              Redis 회로를 강제로 열면 이후 이벤트는 MySQL 집계로 감지됩니다. 실험이 끝나면 반드시 닫으세요.
            </p>
            {st && (
              <div className="flex items-center justify-between">
                <Status tone={modeTone[st.detectionMode]}>{modeLabel[st.detectionMode]}</Status>
                <span className="text-ink2">
                  회로 {st.redisBreaker.state}
                  {st.redisBreaker.forced ? ', 강제 열림' : ''}
                </span>
              </div>
            )}
            <div className="flex gap-2">
              <button className="btn" onClick={() => breaker.mutate(true)} disabled={breaker.isPending || st?.redisBreaker.forced}>
                회로 열기 (fallback)
              </button>
              <button className="btn" onClick={() => breaker.mutate(false)} disabled={breaker.isPending || (st ? !st.redisBreaker.forced : false)}>
                회로 닫기 (정상)
              </button>
            </div>
          </div>
        </Panel>
      </div>

      <Panel
        title="응답 기록"
        aside={
          log.length > 0 && (
            <span>
              {num(log.length)}건 전송, 알림 {num(triggered)}건, 억제 {num(suppressed)}건
            </span>
          )
        }
      >
        {log.length === 0 ? (
          <Empty>{running ? '보내는 중입니다.' : '보낸 이벤트의 판정 결과가 여기에 쌓입니다.'}</Empty>
        ) : (
          <div className="max-h-[70vh] overflow-auto">
            <table className="data">
              <thead>
                <tr>
                  <th className="num">#</th>
                  <th>시각</th>
                  <th>판정</th>
                  <th className="num">건수/임계</th>
                  <th>경로</th>
                  <th>비고</th>
                </tr>
              </thead>
              <tbody>
                {log.map((r) => {
                  const ev = r.result?.evaluations ?? []
                  return (
                    <tr key={r.i}>
                      <td className="num text-ink2">{r.i}</td>
                      <td className="whitespace-nowrap">{fmtTime(r.at)}</td>
                      <td>
                        {r.error ? (
                          <Status tone="critical">실패</Status>
                        ) : r.result?.duplicate ? (
                          <Status tone="neutral">중복</Status>
                        ) : ev.length === 0 ? (
                          <Status tone="neutral">정책 없음</Status>
                        ) : (
                          <span className="flex flex-col gap-0.5">
                            {ev.map((e) => (
                              <Status key={e.policyId} tone={resultTone[e.result]}>
                                {resultLabel[e.result]}
                              </Status>
                            ))}
                          </span>
                        )}
                      </td>
                      <td className="num">{ev.map((e) => `${e.count}/${e.threshold}`).join(' ')}</td>
                      <td>{ev.map((e) => pathLabel[e.path]).join(' ')}</td>
                      <td className="text-ink2">
                        {r.error ?? ev.filter((e) => e.alertId).map((e) => `알림 #${e.alertId}`).join(' ')}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </Panel>
    </div>
  )
}
