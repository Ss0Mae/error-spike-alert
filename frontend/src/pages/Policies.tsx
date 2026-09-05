import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useApp } from '../App'
import { ENVS, api, type Policy, type PolicyInput } from '../api'
import { Empty, ErrorNote, Field, Panel, Status, num, secs, shortFp } from '../ui'

const blank = (projectId: number): PolicyInput => ({
  projectId,
  environment: 'PRODUCTION',
  scope: 'PER_FINGERPRINT',
  targetFingerprint: '',
  windowSeconds: 60,
  threshold: 20,
  cooldownSeconds: 300,
  channel: 'WEBHOOK',
  webhookUrl: 'http://localhost:8091/webhook',
  enabled: true,
})

export default function PoliciesPage() {
  const { project } = useApp()
  const qc = useQueryClient()
  const [draft, setDraft] = useState<(PolicyInput & { id?: number }) | null>(null)
  const [confirmDelete, setConfirmDelete] = useState<number | null>(null)

  const q = useQuery({ queryKey: ['policies', project?.id], enabled: !!project, queryFn: () => api.policies(project!.id) })
  const invalidate = () => qc.invalidateQueries({ queryKey: ['policies'] })

  const save = useMutation({
    mutationFn: (d: PolicyInput & { id?: number }) => {
      const body: PolicyInput = { ...d, targetFingerprint: d.targetFingerprint?.trim() || null }
      return d.id ? api.patchPolicy(d.id, body) : api.createPolicy(body)
    },
    onSuccess: () => {
      invalidate()
      setDraft(null)
    },
  })
  const toggle = useMutation({
    mutationFn: (p: Policy) => api.patchPolicy(p.id, { enabled: !p.enabled }),
    onSuccess: invalidate,
  })
  const remove = useMutation({
    mutationFn: (id: number) => api.deletePolicy(id),
    onSuccess: () => {
      invalidate()
      setConfirmDelete(null)
    },
  })

  if (!project) return <Empty>프로젝트를 불러오는 중입니다.</Empty>

  const set = <K extends keyof PolicyInput>(k: K, v: PolicyInput[K]) => setDraft((d) => (d ? { ...d, [k]: v } : d))
  const submit = (e: FormEvent) => {
    e.preventDefault()
    if (draft) save.mutate(draft)
  }

  return (
    <div className="grid gap-4">
      <Panel
        title="알림 정책"
        aside={
          !draft && (
            <button className="btn btn-primary h-7" onClick={() => setDraft(blank(project.id))}>
              새 정책
            </button>
          )
        }
      >
        <ErrorNote error={q.error ?? toggle.error ?? remove.error} />
        {q.data && q.data.length === 0 ? (
          <Empty>정책이 없습니다. 새 정책을 만들면 그 조건부터 감지가 시작됩니다.</Empty>
        ) : (
          <div className="overflow-x-auto">
            <table className="data">
              <thead>
                <tr>
                  <th className="num">ID</th>
                  <th>상태</th>
                  <th>환경</th>
                  <th>대상</th>
                  <th>조건</th>
                  <th>cooldown</th>
                  <th>webhook</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {q.data?.map((p) => (
                  <tr key={p.id} className={p.enabled ? '' : 'text-ink2'}>
                    <td className="num">{p.id}</td>
                    <td>
                      <button
                        className="text-left"
                        onClick={() => toggle.mutate(p)}
                        disabled={toggle.isPending}
                        aria-label={p.enabled ? '정책 끄기' : '정책 켜기'}
                      >
                        <Status tone={p.enabled ? 'good' : 'neutral'}>{p.enabled ? '켜짐' : '꺼짐'}</Status>
                      </button>
                    </td>
                    <td>{p.environment}</td>
                    <td>
                      {p.scope === 'ALL_ERRORS' ? '모든 에러 합산' : p.targetFingerprint ? `fingerprint ${shortFp(p.targetFingerprint)}` : 'fingerprint별'}
                    </td>
                    <td className="whitespace-nowrap">
                      {secs(p.windowSeconds)} 안에 {num(p.threshold)}건 이상
                    </td>
                    <td className="whitespace-nowrap">{p.cooldownSeconds === 0 ? '없음' : secs(p.cooldownSeconds)}</td>
                    <td className="max-w-[260px] truncate text-ink2">{p.webhookUrl}</td>
                    <td className="whitespace-nowrap text-right">
                      <button className="btn h-7" onClick={() => setDraft({ ...p, targetFingerprint: p.targetFingerprint ?? '' })}>
                        수정
                      </button>{' '}
                      {confirmDelete === p.id ? (
                        <>
                          <button className="btn btn-danger h-7" onClick={() => remove.mutate(p.id)} disabled={remove.isPending}>
                            정말 삭제
                          </button>{' '}
                          <button className="btn h-7" onClick={() => setConfirmDelete(null)}>
                            취소
                          </button>
                        </>
                      ) : (
                        <button className="btn h-7" onClick={() => setConfirmDelete(p.id)}>
                          삭제
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {draft && (
        <Panel title={draft.id ? `정책 ${draft.id} 수정` : '새 정책'}>
          <form onSubmit={submit} className="grid gap-3 px-4 pb-4 sm:grid-cols-3">
            <Field label="환경">
              <select className="input w-full" value={draft.environment} onChange={(e) => set('environment', e.target.value as PolicyInput['environment'])}>
                {ENVS.map((e) => (
                  <option key={e}>{e}</option>
                ))}
              </select>
            </Field>
            <Field label="집계 범위">
              <select className="input w-full" value={draft.scope} onChange={(e) => set('scope', e.target.value as PolicyInput['scope'])}>
                <option value="PER_FINGERPRINT">fingerprint별로 따로 센다</option>
                <option value="ALL_ERRORS">모든 에러를 합쳐서 센다</option>
              </select>
            </Field>
            <Field label="특정 fingerprint만 (비우면 전부)">
              <input
                className="input w-full font-mono text-[12px]"
                value={draft.targetFingerprint ?? ''}
                onChange={(e) => set('targetFingerprint', e.target.value)}
                disabled={draft.scope === 'ALL_ERRORS'}
              />
            </Field>
            <Field label="윈도우 (초, 1–3600)">
              <input className="input w-full" type="number" min={1} max={3600} required value={draft.windowSeconds} onChange={(e) => set('windowSeconds', Number(e.target.value))} />
            </Field>
            <Field label="임계값 (건, 1 이상)">
              <input className="input w-full" type="number" min={1} required value={draft.threshold} onChange={(e) => set('threshold', Number(e.target.value))} />
            </Field>
            <Field label="cooldown (초, 0이면 매번 알림)">
              <input className="input w-full" type="number" min={0} max={86400} required value={draft.cooldownSeconds} onChange={(e) => set('cooldownSeconds', Number(e.target.value))} />
            </Field>
            <Field label="webhook URL" className="sm:col-span-2">
              <input className="input w-full" type="url" required value={draft.webhookUrl} onChange={(e) => set('webhookUrl', e.target.value)} />
            </Field>
            <label className="flex items-end gap-2 pb-1.5 text-[13px]">
              <input type="checkbox" checked={draft.enabled} onChange={(e) => set('enabled', e.target.checked)} />
              바로 켜기
            </label>
            <div className="flex gap-2 sm:col-span-3">
              <button className="btn btn-primary" type="submit" disabled={save.isPending}>
                {draft.id ? '변경 저장' : '정책 만들기'}
              </button>
              <button className="btn" type="button" onClick={() => setDraft(null)}>
                취소
              </button>
            </div>
            <div className="sm:col-span-3">
              <ErrorNote error={save.error} />
            </div>
          </form>
        </Panel>
      )}
    </div>
  )
}
