import { api } from "./client"
import type {
  Activity,
  DashboardSummary,
  FlakyTest,
  PassRatePoint,
  SeverityCount,
} from "@/types/dashboard"

const get = <T>(path: string, projectId: number, extra?: Record<string, number>) =>
  api.get<T>(`/api/dashboard/${path}`, { params: { projectId, ...extra } }).then((r) => r.data)

export const fetchSummary = (projectId: number) => get<DashboardSummary>("summary", projectId)

export const fetchPassRateTrend = (projectId: number, days = 30) =>
  get<PassRatePoint[]>("pass-rate-trend", projectId, { days })

export const fetchDefectsBySeverity = (projectId: number) =>
  get<SeverityCount[]>("defects-by-severity", projectId)

export const fetchRecentActivity = (projectId: number, limit = 10) =>
  get<Activity[]>("recent-activity", projectId, { limit })

export const fetchFlakyTests = (projectId: number, runs = 10) =>
  get<FlakyTest[]>("flaky-tests", projectId, { runs })
