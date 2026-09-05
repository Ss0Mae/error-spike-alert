// 백엔드 API 계약 (docs/DESIGN.md §6). 봉투 {success, data, error} 를 벗겨서 data만 돌려준다.

export type Env = 'LOCAL' | 'DEV' | 'STAGING' | 'PRODUCTION'
export const ENVS: Env[] = ['LOCAL', 'DEV', 'STAGING', 'PRODUCTION']

export type DetectionMode = 'NORMAL' | 'FALLBACK' | 'DEGRADED'
export type DetectionPath = 'REDIS' | 'DB_FALLBACK' | 'NONE'
export type EvalResult = 'TRIGGERED' | 'NOT_TRIGGERED' | 'SUPPRESSED' | 'SKIPPED'
export type AlertStatus = 'PENDING' | 'SENT' | 'FAILED'

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

export interface Project {
  id: number
  name: string
  apiKey: string
  createdAt: string
}

export interface TrendBucket {
  ts: string | number
  count: number
}
export interface Trend {
  buckets: TrendBucket[]
  recent: Record<'1m' | '5m' | '1h' | '24h', number>
  topFingerprints: { fingerprint: string; errorType: string; count: number }[]
}

export interface ErrorRow {
  id: number
  environment: Env
  fingerprint: string
  errorType: string
  message: string | null
  occurredAt: string
  receivedAt: string
  requestId?: string | null
  traceId?: string | null
  serverInstance?: string | null
}
export interface ErrorDetail extends ErrorRow {
  eventId: string
  stackTrace?: string | null
  metadata?: Record<string, unknown> | null
}

export interface IngestBody {
  eventId?: string
  environment: Env
  errorType: string
  fingerprint?: string
  message: string
  stackTrace?: string
  occurredAt?: string
  requestId?: string
  traceId?: string
  serverInstance?: string
  metadata?: Record<string, unknown>
}
export interface Evaluation {
  policyId: number
  count: number
  threshold: number
  result: EvalResult
  path: DetectionPath
  alertId?: number
}
export interface IngestResult {
  errorId: number
  eventId: string
  fingerprint: string
  duplicate: boolean
  receivedAt: string
  evaluations: Evaluation[]
}

export interface Policy {
  id: number
  projectId: number
  environment: Env
  scope: 'PER_FINGERPRINT' | 'ALL_ERRORS'
  targetFingerprint?: string | null
  windowSeconds: number
  threshold: number
  cooldownSeconds: number
  channel: 'WEBHOOK'
  webhookUrl: string
  enabled: boolean
  createdAt: string
  updatedAt: string
}
export type PolicyInput = Omit<Policy, 'id' | 'createdAt' | 'updatedAt'>

export interface Alert {
  id: number
  policyId: number
  fingerprint: string | null
  detectedCount: number
  detectedAt: string
  windowStartedAt: string
  windowEndedAt: string
  status: AlertStatus
  attemptCount: number
  sentAt: string | null
  failureReason: string | null
  detectionPath: DetectionPath
}
export interface AlertDetail extends Alert {
  triggerEventId: number | null
  dedupKey: string
}

export interface Cooldown {
  policyId: number
  fingerprint: string | null
  remainingSeconds: number
}

export interface SystemStatus {
  detectionMode: DetectionMode
  redisBreaker: { state: 'CLOSED' | 'OPEN' | 'HALF_OPEN'; openUntil?: string | null; forced: boolean }
  fallbackPermitsAvailable: number
  executor: { active: number; queueSize: number; queueCapacity: number; poolSize: number }
}

export class ApiError extends Error {
  code: string
  status: number
  constructor(code: string, message: string, status: number) {
    super(message)
    this.code = code
    this.status = status
  }
}

const TOKEN_KEY = 'spike.adminToken'
export const getAdminToken = () => localStorage.getItem(TOKEN_KEY) ?? 'admin-token'
export const setAdminToken = (t: string) => localStorage.setItem(TOKEN_KEY, t)

type Params = Record<string, string | number | boolean | null | undefined>
const qs = (p: Params) => {
  const s = new URLSearchParams()
  for (const [k, v] of Object.entries(p)) if (v !== undefined && v !== null && v !== '') s.set(k, String(v))
  const str = s.toString()
  return str ? `?${str}` : ''
}

async function request<T>(path: string, init: RequestInit & { apiKey?: string } = {}): Promise<T> {
  const { apiKey, ...rest } = init
  const headers: Record<string, string> = { Accept: 'application/json', ...(rest.headers as Record<string, string>) }
  if (rest.body) headers['Content-Type'] = 'application/json'
  if (apiKey) headers['X-API-Key'] = apiKey
  else headers['X-Admin-Token'] = getAdminToken()
  let res: Response
  try {
    res = await fetch(path, { ...rest, headers })
  } catch {
    throw new ApiError('NETWORK', '서버에 연결할 수 없습니다. 백엔드(8090)가 떠 있는지 확인하세요.', 0)
  }
  if (res.status === 204) return undefined as T
  const body = await res.json().catch(() => null)
  if (!res.ok || !body?.success) {
    throw new ApiError(body?.error?.code ?? `HTTP_${res.status}`, body?.error?.message ?? res.statusText, res.status)
  }
  return body.data as T
}

const json = (body: unknown) => JSON.stringify(body)

export const api = {
  projects: () => request<Project[]>('/api/projects'),

  trend: (p: { projectId: number; environment?: Env | ''; fingerprint?: string; from?: string; to?: string; interval: '1m' | '5m' | '1h' }) =>
    request<Trend>(`/api/errors/trend${qs(p)}`),
  errors: (p: { projectId: number; environment?: string; fingerprint?: string; requestId?: string; page: number; size: number }) =>
    request<Page<ErrorRow>>(`/api/errors${qs(p)}`),
  error: (id: number | string) => request<ErrorDetail>(`/api/errors/${id}`),
  ingest: (apiKey: string, body: IngestBody) =>
    request<IngestResult>('/api/errors', { method: 'POST', body: json(body), apiKey }),

  policies: (projectId: number) => request<Policy[]>(`/api/alert-policies${qs({ projectId })}`),
  createPolicy: (body: PolicyInput) => request<Policy>('/api/alert-policies', { method: 'POST', body: json(body) }),
  patchPolicy: (id: number, body: Partial<PolicyInput>) =>
    request<Policy>(`/api/alert-policies/${id}`, { method: 'PATCH', body: json(body) }),
  deletePolicy: (id: number) => request<void>(`/api/alert-policies/${id}`, { method: 'DELETE' }),

  alerts: (p: { projectId: number; policyId?: number; status?: string; page: number; size: number }) =>
    request<Page<Alert>>(`/api/alerts${qs(p)}`),
  alert: (id: number) => request<AlertDetail>(`/api/alerts/${id}`),
  retryAlert: (id: number) => request<Alert>(`/api/alerts/${id}/retry`, { method: 'POST' }),
  cooldowns: (projectId: number) => request<Cooldown[]>(`/api/alerts/cooldowns${qs({ projectId })}`),

  status: () => request<SystemStatus>('/api/system/status'),
  setBreaker: (forceOpen: boolean) =>
    request<SystemStatus>('/api/system/redis-breaker', { method: 'POST', body: json({ forceOpen }) }),
}
