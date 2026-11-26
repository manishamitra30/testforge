import { api } from "./client"
import type { PageResponse } from "@/types"
import type { ResultStatus, Run, RunDetail, RunLogChunk, RunResult } from "@/types/run"

export const fetchRun = (id: number) =>
  api.get<RunDetail>(`/api/runs/${id}`).then((r) => r.data)

export const fetchRunLog = (id: number, from = 0) =>
  api.get<RunLogChunk>(`/api/runs/${id}/log`, { params: { from } }).then((r) => r.data)

export const fetchRuns = (projectId: number, page = 0, size = 100) =>
  api
    .get<PageResponse<Run>>("/api/runs", { params: { projectId, page, size } })
    .then((r) => r.data)

export const createRun = (suiteId: number, name: string) =>
  api.post<Run>("/api/runs", { suiteId, name }).then((r) => r.data)

export interface ResultUpdate {
  status: ResultStatus
  comment?: string
  durationMs?: number
}

// The backend replaces the comment on every update, so always send the current comment text.
export const updateResult = (runId: number, resultId: number, data: ResultUpdate) =>
  api.patch<RunResult>(`/api/runs/${runId}/results/${resultId}`, data).then((r) => r.data)

export const abortRun = (runId: number) => api.post<Run>(`/api/runs/${runId}/abort`).then((r) => r.data)
