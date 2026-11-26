import { api } from "./client"
import type { PageResponse } from "@/types"
import type { Severity } from "@/types/dashboard"
import type { CreateDefectRequest, Defect, DefectStatus } from "@/types/defect"

export const createDefect = (data: CreateDefectRequest) =>
  api.post<Defect>("/api/defects", data).then((r) => r.data)

export const fetchDefects = (projectId: number, severity?: Severity) =>
  api
    .get<PageResponse<Defect>>("/api/defects", {
      params: { projectId, severity, page: 0, size: 200 },
    })
    .then((r) => r.data)

export const changeDefectStatus = (id: number, status: DefectStatus) =>
  api.patch<Defect>(`/api/defects/${id}/status`, { status }).then((r) => r.data)
