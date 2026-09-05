import { useQuery } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { useApp } from '../App'
import { ENVS, api } from '../api'
import { Empty, ErrorNote, Field, Pager, Panel, fmtDateTime, fmtTime, shortFp } from '../ui'

const SIZE = 50

export function ErrorsPage() {
  const { project } = useApp()
  const [sp, setSp] = useSearchParams()
  const environment = sp.get('environment') ?? ''
  const fingerprint = sp.get('fingerprint') ?? ''
  const requestId = sp.get('requestId') ?? ''
  const page = Number(sp.get('page') ?? 0)
  const [draftFp, setDraftFp] = useState(fingerprint)
  const [draftReq, setDraftReq] = useState(requestId)

  const patch = (next: Record<string, string>) => {
    const n = new URLSearchParams(sp)
    for (const [k, v] of Object.entries(next)) {
      if (v) n.set(k, v)
      else n.delete(k)
    }
    if (!('page' in next)) n.delete('page')
    setSp(n)
  }
  const submit = (e: FormEvent) => {
    e.preventDefault()
    patch({ fingerprint: draftFp.trim(), requestId: draftReq.trim() })
  }

  const q = useQuery({
    queryKey: ['errors', project?.id, environment, fingerprint, requestId, page],
    enabled: !!project,
    refetchInterval: 5_000,
    queryFn: () => api.errors({ projectId: project!.id, environment, fingerprint, requestId, page, size: SIZE }),
  })

  if (!project) return <Empty>프로젝트를 불러오는 중입니다.</Empty>

  return (
    <Panel title="에러 이벤트" aside="수신 시각 역순">
      <form onSubmit={submit} className="flex flex-wrap items-end gap-3 px-4 pb-3">
        <Field label="환경">
          <select className="input" value={environment} onChange={(e) => patch({ environment: e.target.value })}>
            <option value="">전체</option>
            {ENVS.map((e) => (
              <option key={e}>{e}</option>
            ))}
          </select>
        </Field>
        <Field label="fingerprint">
          <input className="input w-72 font-mono text-[12px]" value={draftFp} onChange={(e) => setDraftFp(e.target.value)} placeholder="정확히 일치" />
        </Field>
        <Field label="요청 ID">
          <input className="input w-44" value={draftReq} onChange={(e) => setDraftReq(e.target.value)} />
        </Field>
        <button className="btn" type="submit">
          필터 적용
        </button>
        {(fingerprint || requestId || environment) && (
          <button
            className="btn"
            type="button"
            onClick={() => {
              setDraftFp('')
              setDraftReq('')
              patch({ environment: '', fingerprint: '', requestId: '' })
            }}
          >
            필터 지우기
          </button>
        )}
      </form>
      <ErrorNote error={q.error} />
      {q.data && q.data.content.length === 0 ? (
        <Empty>조건에 맞는 에러가 없습니다.</Empty>
      ) : (
        <div className="overflow-x-auto">
          <table className="data">
            <thead>
              <tr>
                <th>수신</th>
                <th>환경</th>
                <th>에러 종류</th>
                <th>메시지</th>
                <th>fingerprint</th>
                <th>서버</th>
              </tr>
            </thead>
            <tbody>
              {q.data?.content.map((r) => (
                <tr key={r.id}>
                  <td className="whitespace-nowrap">
                    <Link className="text-cobalt hover:underline" to={`/errors/${r.id}`}>
                      {fmtTime(r.receivedAt)}
                    </Link>
                  </td>
                  <td>{r.environment}</td>
                  <td className="max-w-[260px] truncate">{r.errorType}</td>
                  <td className="max-w-[360px] truncate text-ink2">{r.message}</td>
                  <td>
                    <button className="text-cobalt hover:underline" onClick={() => {
                        setDraftFp(r.fingerprint)
                        patch({ fingerprint: r.fingerprint })
                      }}>
                      {shortFp(r.fingerprint)}
                    </button>
                  </td>
                  <td className="text-ink2">{r.serverInstance ?? '–'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {q.data && <Pager page={page} size={SIZE} total={q.data.totalElements} onPage={(p) => patch({ page: String(p) })} />}
    </Panel>
  )
}

export function ErrorDetailPage() {
  const { id } = useParams()
  const q = useQuery({ queryKey: ['error', id], queryFn: () => api.error(id!), enabled: !!id })
  const e = q.data

  return (
    <div className="grid gap-4">
      <Link to="/errors" className="text-[13px] text-cobalt hover:underline">
        에러 목록으로
      </Link>
      <ErrorNote error={q.error} />
      {e && (
        <>
          <Panel title={e.errorType} aside={`#${e.id}`}>
            <p className="px-4 pb-3 text-[14px]">{e.message ?? <span className="text-ink2">메시지 없음</span>}</p>
            <dl className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-1.5 border-t border-rule px-4 py-3 text-[13px] sm:grid-cols-[auto_1fr_auto_1fr]">
              <dt className="text-ink2">환경</dt>
              <dd>{e.environment}</dd>
              <dt className="text-ink2">fingerprint</dt>
              <dd className="break-all font-mono text-[12px]">{e.fingerprint}</dd>
              <dt className="text-ink2">발생 시각</dt>
              <dd>{fmtDateTime(e.occurredAt)}</dd>
              <dt className="text-ink2">수신 시각</dt>
              <dd>{fmtDateTime(e.receivedAt)}</dd>
              <dt className="text-ink2">이벤트 ID</dt>
              <dd className="break-all font-mono text-[12px]">{e.eventId}</dd>
              <dt className="text-ink2">서버</dt>
              <dd>{e.serverInstance ?? '–'}</dd>
              <dt className="text-ink2">요청 ID</dt>
              <dd className="break-all">{e.requestId ?? '–'}</dd>
              <dt className="text-ink2">트레이스 ID</dt>
              <dd className="break-all">{e.traceId ?? '–'}</dd>
            </dl>
          </Panel>
          <Panel title="Stack trace">
            {e.stackTrace ? (
              <pre className="overflow-x-auto px-4 pb-4 font-mono text-[12px] leading-5 text-ink">{e.stackTrace}</pre>
            ) : (
              <Empty>이 이벤트에는 stack trace가 없습니다.</Empty>
            )}
          </Panel>
          <Panel title="metadata">
            {e.metadata && Object.keys(e.metadata).length > 0 ? (
              <pre className="overflow-x-auto px-4 pb-4 font-mono text-[12px] leading-5">{JSON.stringify(e.metadata, null, 2)}</pre>
            ) : (
              <Empty>추가 정보가 없습니다.</Empty>
            )}
          </Panel>
        </>
      )}
    </div>
  )
}
