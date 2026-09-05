import type { ReactNode } from 'react'
import type { AlertStatus, DetectionMode, DetectionPath, EvalResult } from './api'
import { ApiError } from './api'

// ---------- 포맷 ----------
const nf = new Intl.NumberFormat('ko-KR')
export const num = (n: number | null | undefined) => (n == null ? '–' : nf.format(n))

const pad = (n: number) => String(n).padStart(2, '0')
export function fmtTime(iso: string | number | null | undefined) {
  if (!iso) return '–'
  const d = new Date(iso)
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}
export function fmtDateTime(iso: string | number | null | undefined) {
  if (!iso) return '–'
  const d = new Date(iso)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${fmtTime(d.getTime())}`
}
export const shortFp = (fp: string | null | undefined) => (fp ? (fp.length > 14 ? fp.slice(0, 14) + '…' : fp) : '전체')
export const secs = (s: number) => (s >= 60 ? `${Math.floor(s / 60)}분 ${pad(s % 60)}초` : `${s}초`)

// ---------- 상태 표시: 색 + 아이콘 + 글자를 항상 함께 ----------
type Tone = 'good' | 'warn' | 'critical' | 'serious' | 'neutral' | 'cobalt'

const toneClass: Record<Tone, string> = {
  good: 'text-good',
  warn: 'text-[#8a5a00]',
  critical: 'text-critical',
  serious: 'text-[#b4491f]',
  neutral: 'text-ink2',
  cobalt: 'text-cobalt',
}
const toneFill: Record<Tone, string> = {
  good: '#0ca30c',
  warn: '#fab219',
  critical: '#d03b3b',
  serious: '#ec835a',
  neutral: '#8a98a6',
  cobalt: '#2456e6',
}

function Glyph({ tone }: { tone: Tone }) {
  const fill = toneFill[tone]
  // good=원, warn=삼각형, critical=팔각형, serious=마름모, neutral=선, cobalt=원
  const path =
    tone === 'warn'
      ? 'M6 1.5 11 10.5H1z'
      : tone === 'critical'
        ? 'M4 1h4l3 3v4l-3 3H4L1 8V4z'
        : tone === 'serious'
          ? 'M6 1l5 5-5 5-5-5z'
          : tone === 'neutral'
            ? 'M1 5.5h10v1H1z'
            : 'M6 1a5 5 0 1 1 0 10A5 5 0 0 1 6 1z'
  return (
    <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true" className="shrink-0">
      <path d={path} fill={fill} />
    </svg>
  )
}

export function Status({ tone, children }: { tone: Tone; children: ReactNode }) {
  return (
    <span className={`inline-flex items-center gap-1.5 whitespace-nowrap text-[13px] font-medium ${toneClass[tone]}`}>
      <Glyph tone={tone} />
      {children}
    </span>
  )
}

export const modeTone: Record<DetectionMode, Tone> = { NORMAL: 'good', FALLBACK: 'warn', DEGRADED: 'critical' }
export const modeLabel: Record<DetectionMode, string> = {
  NORMAL: 'Redis 정상 경로',
  FALLBACK: 'DB fallback 중',
  DEGRADED: '감지 제한 중',
}
export const alertTone: Record<AlertStatus, Tone> = { SENT: 'good', PENDING: 'warn', FAILED: 'critical' }
export const alertLabel: Record<AlertStatus, string> = { SENT: '발송됨', PENDING: '대기', FAILED: '실패' }
export const resultTone: Record<EvalResult, Tone> = {
  TRIGGERED: 'serious',
  NOT_TRIGGERED: 'neutral',
  SUPPRESSED: 'cobalt',
  SKIPPED: 'warn',
}
export const resultLabel: Record<EvalResult, string> = {
  TRIGGERED: '알림 발생',
  NOT_TRIGGERED: '임계값 미만',
  SUPPRESSED: 'cooldown 억제',
  SKIPPED: '감지 생략',
}
export const pathLabel: Record<DetectionPath, string> = { REDIS: 'Redis', DB_FALLBACK: 'DB fallback', NONE: '–' }

// ---------- 레이아웃 조각 ----------
export function Panel({ title, aside, children, className = '' }: { title?: ReactNode; aside?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <section className={`panel ${className}`}>
      {title && (
        <h2 className="panel-title">
          <span>{title}</span>
          {aside && <span className="text-[12px] font-normal text-ink2">{aside}</span>}
        </h2>
      )}
      {children}
    </section>
  )
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="px-4 py-8 text-center text-[13px] text-ink2">{children}</p>
}

export function ErrorNote({ error }: { error: unknown }) {
  if (!error) return null
  const e = error as Partial<ApiError>
  const text = e instanceof ApiError ? `${e.code}: ${e.message}` : String((error as Error)?.message ?? error)
  const hint =
    e instanceof ApiError && e.status === 401 ? ' 상단의 관리자 토큰을 확인하세요.' : ''
  return (
    <p role="alert" className="mx-4 my-3 rounded-[3px] border border-critical/40 bg-[#fdf3f3] px-3 py-2 text-[13px] text-critical">
      {text}
      {hint}
    </p>
  )
}

export function Pager({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (p: number) => void }) {
  const last = Math.max(0, Math.ceil(total / size) - 1)
  return (
    <div className="flex items-center justify-between px-4 py-2 text-[12px] text-ink2">
      <span>
        전체 {num(total)}건 중 {total === 0 ? 0 : num(page * size + 1)}–{num(Math.min(total, (page + 1) * size))}
      </span>
      <span className="flex gap-1">
        <button className="btn h-7" disabled={page <= 0} onClick={() => onPage(page - 1)}>
          이전
        </button>
        <button className="btn h-7" disabled={page >= last} onClick={() => onPage(page + 1)}>
          다음
        </button>
      </span>
    </div>
  )
}

export function Field({ label, children, className = '' }: { label: string; children: ReactNode; className?: string }) {
  return (
    <label className={`block ${className}`}>
      <span className="label">{label}</span>
      {children}
    </label>
  )
}
