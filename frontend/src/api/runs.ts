import { api } from "./client"
import type { RunDetail, RunLogChunk } from "@/types/run"

export const fetchRun = (id: number) =>
  api.get<RunDetail>(`/api/runs/${id}`).then((r) => r.data)

export const fetchRunLog = (id: number, from = 0) =>
  api.get<RunLogChunk>(`/api/runs/${id}/log`, { params: { from } }).then((r) => r.data)