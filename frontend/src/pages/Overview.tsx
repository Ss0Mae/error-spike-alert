import { useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis, type TooltipProps } from 'recharts'
import { useApp } from '../App'
import { api, type Trend } from '../api'
import { Empty, ErrorNote, Panel, Status, fmtDateTime, fmtTime, modeLabel, modeTone, num, secs, shortFp } from '../ui'

type Interval = '1m' | '5m' | '1h'
const INTERVALS: Record<Interval, { span: number; step: number; label: string }> = {
  '1m': { span: 60 * 60_000, step: 60_000, label: '최근 1시간, 1분 간격' },
  '5m': { span: 6 * 60 * 60_000, step: 5 * 60_000, label: '최근 6시간, 5분 간격' },
  '1h': { span: 24 * 60 * 60_000, step: 60 * 60_000, label: '최근 24시간, 1시간 간격' },
}

// 백엔드는 이벤트가 있는 버킷만 돌려줄 수 있으므로 빈 버킷을 0으로 채워 기록지처럼 연속으로 그린다.
function fillBuckets(trend: Trend, from: number, to: number, step: number) {
  const have = new Map<number, number>()
  for (const b of trend.buckets) {
    const t = Math.floor(new Date(b.ts).getTime() / step) * step
    have.set(t, (have.get(t) ?? 0) + b.count)
  }
  const out: { t: number; count: number }[] = []
  for (let t = Math.floor(from / step) * step; t <= to; t += step) out.push({ t, count: have.get(t) ?? 0 })
  return out
}

const hm = (t: number) => {
  const d = new Date(t)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

function TrendTip({ active, payload }: TooltipProps<number, string>) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload as { t: number; count: number }
  return (
    <div className="rounded-[3px] border border-rule bg-surface px-2.5 py-1.5 text-[12px] shadow-none">
      <div className="text-ink2">{fmtDateTime(p.t)}</div>
      <div className="font-medium">{num(p.count)}건</div>
    </div>
  )
}

export default function OverviewPage() {
  const { project } = useApp()
  const [interval, setInterval_] = useState<Interval>('1m')
  const cfg = INTERVALS[interval]

  const trendQ = useQuery({
    queryKey: ['trend', project?.id, interval],
    enabled: !!project,
    refetchInterval: 5_000,
    queryFn: async () => {
      const to = Date.now()
      const from = to - cfg.span
      const trend = await api.trend({
        projectId: project!.id,
        interval,
        from: new Date(from).toISOString(),
        to: new Date(to).toISOString(),
      })
      return { trend, data: fillBuckets(trend, from, to, cfg.step) }
    },
  })
  const statusQ = useQuery({ queryKey: ['status'], queryFn: api.status, refetchInterval: 5_000 })
  const cooldownQ = useQuery({
    queryKey: ['cooldowns', project?.id],
    enabled: !!project,
    refetchInterval: 5_000,
    queryFn: () => api.cooldowns(project!.id),
  })

  // 서버가 준 남은 시간에서 마지막 조회 이후 흐른 초를 빼서 1초 단위로 줄인다.
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const id = window.setInterval(() => setNow(Date.now()), 1_000)
    return () => window.clearInterval(id)
  }, [])
  const elapsed = Math.floor((now - (cooldownQ.dataUpdatedAt || now)) / 1000)

  if (!project) return <Empty>프로젝트를 불러오는 중입니다.</Empty>
  const trend = trendQ.data?.trend
  const st = statusQ.data
  const peak = trendQ.data ? Math.max(0, ...trendQ.data.data.map((d) => d.count)) : 0

  return (
    <div className="grid gap-4">
      {/* 최근 집계: 카드 대신 한 줄 눈금 */}
      <section className="panel">
        <dl className="grid grid-cols-2 divide-rule sm:grid-cols-4 sm:divide-x">
          {(['1m', '5m', '1h', '24h'] as const).map((k) => (
            <div key={k} className="px-4 py-3">
              <dd className="font-display text-[34px] font-semibold leading-none">{num(trend?.recent?.[k])}</dd>
              <dt className="mt-1.5 text-[12px] text-ink2">
                {k === '1m' ? '최근 1분' : k === '5m' ? '최근 5분' : k === '1h' ? '최근 1시간' : '최근 24시간'}
              </dt>
            </div>
          ))}
        </dl>
      </section>

      <Panel
        title="에러 추이"
        aside={
          <span className="flex items-center gap-3">
            <span>{cfg.label}</span>
            <span className="flex rounded-[3px] border border-rule" role="group" aria-label="구간 간격">
              {(Object.keys(INTERVALS) as Interval[]).map((k) => (
                <button
                  key={k}
                  onClick={() => setInterval_(k)}
                  aria-pressed={interval === k}
                  className={`px-2 py-0.5 text-[12px] ${interval === k ? 'bg-ink text-paper' : 'text-ink2 hover:text-ink'}`}
                >
                  {k}
                </button>
              ))}
            </span>
          </span>
        }
      >
        <ErrorNote error={trendQ.error} />
        <div className="px-2 pb-3">
          {trendQ.data && peak === 0 ? (
            <Empty>이 구간에는 수집된 에러가 없습니다. 테스트 발생 탭에서 이벤트를 보내 보세요.</Empty>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <BarChart data={trendQ.data?.data ?? []} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barCategoryGap={2}>
                <CartesianGrid vertical={false} stroke="#e9eef3" />
                <XAxis
                  dataKey="t"
                  tickFormatter={hm}
                  tick={{ fontSize: 11, fill: '#5b6b7a' }}
                  axisLine={{ stroke: '#d5dde5' }}
                  tickLine={false}
                  minTickGap={40}
                />
                <YAxis width={40} allowDecimals={false} tick={{ fontSize: 11, fill: '#5b6b7a' }} axisLine={false} tickLine={false} />
                <Tooltip cursor={{ fill: 'rgba(36,86,230,0.08)' }} content={<TrendTip />} />
                <Bar dataKey="count" fill="#2456e6" radius={[2, 2, 0, 0]} maxBarSize={18} isAnimationActive={false} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </Panel>

      <div className="grid gap-4 lg:grid-cols-[3fr_2fr]">
        <Panel title="많이 발생한 에러" aside="최근 24시간">
          {trend && trend.topFingerprints.length === 0 ? (
            <Empty>아직 집계된 에러가 없습니다.</Empty>
          ) : (
            <table className="data">
              <thead>
                <tr>
                  <th className="num w-8">#</th>
                  <th>에러 종류</th>
                  <th>fingerprint</th>
                  <th className="num">건수</th>
                </tr>
              </thead>
              <tbody>
                {trend?.topFingerprints.map((f, i) => (
                  <tr key={f.fingerprint}>
                    <td className="num text-ink2">{i + 1}</td>
                    <td className="max-w-[320px] truncate">{f.errorType}</td>
                    <td>
                      <Link className="text-cobalt hover:underline" to={`/errors?fingerprint=${encodeURIComponent(f.fingerprint)}`}>
                        {shortFp(f.fingerprint)}
                      </Link>
                    </td>
                    <td className="num font-medium">{num(f.count)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </Panel>

        <div className="grid gap-4 content-start">
          <Panel title="감지기 상태">
            <ErrorNote error={statusQ.error} />
            {st && (
              <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 px-4 pb-3 text-[13px]">
                <dt className="text-ink2">감지 경로</dt>
                <dd>
                  <Status tone={modeTone[st.detectionMode]}>{modeLabel[st.detectionMode]}</Status>
                </dd>
                <dt className="text-ink2">Redis 회로</dt>
                <dd>
                  {st.redisBreaker.state}
                  {st.redisBreaker.forced && <span className="text-ink2"> (실험용 강제 열림)</span>}
                  {st.redisBreaker.openUntil && st.redisBreaker.state !== 'CLOSED' && (
                    <span className="text-ink2"> {fmtTime(st.redisBreaker.openUntil)}까지</span>
                  )}
                </dd>
                <dt className="text-ink2">fallback 여유</dt>
                <dd>{num(st.fallbackPermitsAvailable)}개 동시 조회 가능</dd>
                <dt className="text-ink2">발송 큐</dt>
                <dd>
                  <span className="flex items-center gap-2">
                    <span className="h-1.5 w-32 overflow-hidden rounded-sm bg-paper">
                      <span
                        className={`block h-full ${st.executor.queueSize / st.executor.queueCapacity > 0.8 ? 'bg-critical' : 'bg-cobalt'}`}
                        style={{ width: `${Math.min(100, (100 * st.executor.queueSize) / Math.max(1, st.executor.queueCapacity))}%` }}
                      />
                    </span>
                    {num(st.executor.queueSize)} / {num(st.executor.queueCapacity)}
                  </span>
                </dd>
                <dt className="text-ink2">발송 스레드</dt>
                <dd>
                  {num(st.executor.active)} 작동 중 / {num(st.executor.poolSize)}
                </dd>
              </dl>
            )}
          </Panel>

          <Panel title="활성 cooldown" aside={cooldownQ.data ? `${cooldownQ.data.length}건` : undefined}>
            <ErrorNote error={cooldownQ.error} />
            {cooldownQ.data && cooldownQ.data.length === 0 ? (
              <Empty>억제 중인 알림이 없습니다.</Empty>
            ) : (
              <ul className="px-4 pb-3 text-[13px]">
                {cooldownQ.data?.map((c) => {
                  const left = Math.max(0, c.remainingSeconds - elapsed)
                  return (
                    <li key={`${c.policyId}:${c.fingerprint}`} className="flex items-center justify-between gap-3 border-b border-rule/70 py-1.5 last:border-0">
                      <span>
                        정책 {c.policyId}
                        <span className="text-ink2"> {shortFp(c.fingerprint)}</span>
                      </span>
                      <span className="font-medium">{secs(left)} 남음</span>
                    </li>
                  )
                })}
              </ul>
            )}
          </Panel>
        </div>
      </div>
    </div>
  )
}
