import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApp } from '../App'
import { api, type AlertStatus } from '../api'
import { Empty, ErrorNote, Pager, Panel, Status, alertLabel, alertTone, fmtTime, num, pathLabel, shortFp } from '../ui'

const SIZE = 50
const FILTERS: { v: AlertStatus | ''; label: string }[] = [
  { v: '', label: '전체' },
  { v: 'PENDING', label: '대기' },
  { v: 'SENT', label: '발송됨' },
  { v: 'FAILED', label: '실패' },
]

export default function AlertsPage() {
  const { project } = useApp()
  const qc = useQueryClient()
  const [status, setStatus] = useState<AlertStatus | ''>('')
  const [page, setPage] = useState(0)

  const q = useQuery({
    queryKey: ['alerts', project?.id, status, page],
    enabled: !!project,
    refetchInterval: 5_000,
    queryFn: () => api.alerts({ projectId: project!.id, status, page, size: SIZE }),
  })
  const retry = useMutation({
    mutationFn: (id: number) => api.retryAlert(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['alerts'] }),
  })

  if (!project) return <Empty>프로젝트를 불러오는 중입니다.</Empty>

  return (
    <Panel
      title="알림 이력"
      aside={
        <span className="flex rounded-[3px] border border-rule" role="group" aria-label="상태 필터">
          {FILTERS.map((f) => (
            <button
              key={f.v}
              aria-pressed={status === f.v}
              onClick={() => {
                setStatus(f.v)
                setPage(0)
              }}
              className={`px-2.5 py-0.5 text-[12px] ${status === f.v ? 'bg-ink text-paper' : 'text-ink2 hover:text-ink'}`}
            >
              {f.label}
            </button>
          ))}
        </span>
      }
    >
      <ErrorNote error={q.error ?? retry.error} />
      {q.data && q.data.content.length === 0 ? (
        <Empty>{status ? '이 상태의 알림이 없습니다.' : '아직 발생한 알림이 없습니다. 임계값을 넘는 순간 여기에 기록됩니다.'}</Empty>
      ) : (
        <div className="overflow-x-auto">
          <table className="data">
            <thead>
              <tr>
                <th>감지</th>
                <th>상태</th>
                <th className="num">정책</th>
                <th>fingerprint</th>
                <th className="num">감지 건수</th>
                <th>경로</th>
                <th className="num">시도</th>
                <th>발송</th>
                <th>실패 원인</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {q.data?.content.map((a) => (
                <tr key={a.id}>
                  <td className="whitespace-nowrap">{fmtTime(a.detectedAt)}</td>
                  <td>
                    <Status tone={alertTone[a.status]}>{alertLabel[a.status]}</Status>
                  </td>
                  <td className="num">{a.policyId}</td>
                  <td>
                    {a.fingerprint ? (
                      <Link className="text-cobalt hover:underline" to={`/errors?fingerprint=${encodeURIComponent(a.fingerprint)}`}>
                        {shortFp(a.fingerprint)}
                      </Link>
                    ) : (
                      <span className="text-ink2">전체</span>
                    )}
                  </td>
                  <td className="num font-medium">{num(a.detectedCount)}</td>
                  <td className={a.detectionPath === 'DB_FALLBACK' ? 'text-[#8a5a00]' : ''}>{pathLabel[a.detectionPath]}</td>
                  <td className="num">{a.attemptCount}</td>
                  <td className="whitespace-nowrap">{a.sentAt ? fmtTime(a.sentAt) : '–'}</td>
                  <td className="max-w-[280px] truncate text-ink2" title={a.failureReason ?? undefined}>
                    {a.failureReason ?? ''}
                  </td>
                  <td className="text-right">
                    {a.status === 'FAILED' && (
                      <button className="btn h-7" onClick={() => retry.mutate(a.id)} disabled={retry.isPending}>
                        다시 보내기
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {q.data && <Pager page={page} size={SIZE} total={q.data.totalElements} onPage={setPage} />}
    </Panel>
  )
}
