import { api } from "./client"
import type {
  PageResponse,
  Priority,
  TestCase,
  TestCaseRequest,
  TestCaseStatus,
  TestCaseSummary,
} from "@/types"

export interface CaseFilters {
  projectId: number
  q?: string
  priority?: Priority
  status?: TestCaseStatus
  page: number
  size: number
}

export const fetchCases = (filters: CaseFilters) =>
  api
    .get<PageResponse<TestCaseSummary>>("/api/cases", { params: filters })
    .then((r) => r.data)

export const createCase = (suiteId: number, data: TestCaseRequest) =>
  api.post<TestCase>(`/api/suites/${suiteId}/cases`, data).then((r) => r.data)