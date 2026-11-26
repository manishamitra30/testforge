import { api } from "./client"
import type { CreateDefectRequest, Defect } from "@/types/defect"

export const createDefect = (data: CreateDefectRequest) =>
  api.post<Defect>("/api/defects", data).then((r) => r.data)
