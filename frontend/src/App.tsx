import { useQuery } from '@tanstack/react-query'
import { createContext, useContext, useState } from 'react'
import { NavLink, Route, Routes } from 'react-router-dom'
import { api, getAdminToken, setAdminToken, type Project } from './api'
import AlertsPage from './pages/Alerts'
import { ErrorDetailPage, ErrorsPage } from './pages/Errors'
import FirePage from './pages/Fire'
import OverviewPage from './pages/Overview'
import PoliciesPage from './pages/Policies'
import { modeLabel, num } from './ui'

interface Ctx {
  projects: Project[]
  project: Project | null
  setProjectId: (id: number) => void
  token: string
  setToken: (t: string) => void
}
const AppCtx = createContext<Ctx>(null!)
export const useApp = () => useContext(AppCtx)

const PROJECT_KEY = 'spike.projectId'

const NAV = [
  { to: '/', label: '개요' },
  { to: '/errors', label: '에러' },
  { to: '/policies', label: '정책' },
  { to: '/alerts', label: '알림' },
  { to: '/fire', label: '테스트 발생' },
]

export default function App() {
  const [token, setTokenState] = useState(getAdminToken())
  const setToken = (t: string) => {
    setAdminToken(t)
    setTokenState(t)
  }
  const [projectId, setProjectIdState] = useState(() => Number(localStorage.getItem(PROJECT_KEY)) || 0)
  const setProjectId = (id: number) => {
    localStorage.setItem(PROJECT_KEY, String(id))
    setProjectIdState(id)
  }

  const projectsQ = useQuery({ queryKey: ['projects', token], queryFn: api.projects })
  const projects = projectsQ.data ?? []
  const project = projects.find((p) => p.id === projectId) ?? projects[0] ?? null

  const statusQ = useQuery({ queryKey: ['status', token], queryFn: api.status, refetchInterval: 5_000 })
  const st = statusQ.data
  const mode = st?.detectionMode
  const band =
    mode === 'NORMAL'
      ? 'bg-ink text-paper'
      : mode === 'FALLBACK'
        ? 'bg-warn text-ink'
        : mode === 'DEGRADED'
          ? 'bg-critical text-white'
          : 'bg-ink3 text-white'

  return (
    <AppCtx.Provider value={{ projects, project, setProjectId, token, setToken }}>
      {/* 상태 띠: 페이지에서 유일하게 색을 크게 쓰는 곳. 감지기 상태에 따라 띠 전체가 바뀐다. */}
      <header className={`${band} transition-colors`}>
        <div className="mx-auto flex max-w-[1280px] flex-wrap items-center gap-x-6 gap-y-2 px-4 py-2.5">
          <span className="font-display text-[20px] font-semibold tracking-tight">스파이크 콘솔</span>
          <label className="flex items-center gap-2 text-[13px]">
            <span className="opacity-80">프로젝트</span>
            <select
              className="h-7 rounded-[3px] border border-white/30 bg-white/10 px-2 text-[13px] text-inherit"
              value={project?.id ?? ''}
              onChange={(e) => setProjectId(Number(e.target.value))}
            >
              {projects.length === 0 && <option value="">불러오는 중</option>}
              {projects.map((p) => (
                <option key={p.id} value={p.id} className="text-ink">
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <div className="ml-auto flex items-center gap-5 text-[13px]">
            <span className="font-medium">
              {mode ? modeLabel[mode] : statusQ.isError ? '감지기 응답 없음' : '상태 확인 중'}
            </span>
            {st && (
              <>
                <span className="opacity-80">
                  Redis 회로 {st.redisBreaker.state}
                  {st.redisBreaker.forced ? ' (강제)' : ''}
                </span>
                <span className="opacity-80">
                  발송 큐 {num(st.executor.queueSize)}/{num(st.executor.queueCapacity)}
                </span>
              </>
            )}
          </div>
        </div>
      </header>

      <nav className="border-b border-rule bg-surface">
        <div className="mx-auto flex max-w-[1280px] flex-wrap items-center gap-1 px-2">
          {NAV.map((n) => (
            <NavLink
              key={n.to}
              to={n.to}
              end={n.to === '/'}
              className={({ isActive }) =>
                `px-3 py-2.5 text-[14px] border-b-2 -mb-px ${isActive ? 'border-cobalt text-ink font-medium' : 'border-transparent text-ink2 hover:text-ink'}`
              }
            >
              {n.label}
            </NavLink>
          ))}
          <label className="ml-auto flex items-center gap-2 py-1.5 text-[12px] text-ink2">
            관리자 토큰
            <input
              className="input h-7 w-40 font-mono text-[12px]"
              value={token}
              onChange={(e) => setToken(e.target.value)}
              spellCheck={false}
            />
          </label>
        </div>
      </nav>

      <main className="mx-auto max-w-[1280px] px-4 py-5">
        {projectsQ.isError && (
          <p role="alert" className="panel mb-4 px-4 py-3 text-[13px] text-critical">
            프로젝트 목록을 불러오지 못했습니다. 백엔드가 8090 포트에 떠 있는지, 관리자 토큰이 맞는지 확인하세요.
          </p>
        )}
        <Routes>
          <Route path="/" element={<OverviewPage />} />
          <Route path="/errors" element={<ErrorsPage />} />
          <Route path="/errors/:id" element={<ErrorDetailPage />} />
          <Route path="/policies" element={<PoliciesPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/fire" element={<FirePage />} />
        </Routes>
      </main>
    </AppCtx.Provider>
  )
}
