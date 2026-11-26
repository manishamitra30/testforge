import type { Severity } from "@/types/dashboard"

export type DefectStatus = "OPEN" | "IN_PROGRESS" | "RESOLVED" | "CLOSED"

export interface Defect {
  id: number
  projectId: number
  testResultId: number | null
  runId: number | null
  runName: string | null
  testCaseId: number | null
  testCaseTitle: string | null
  title: string
  description: string | null
  severity: Severity
  status: DefectStatus
  assigneeId: number | null
  assigneeName: string | null
  reporterId: number | null
  reporterName: string | null
  createdAt: string
  updatedAt: string
  allowedTransitions: DefectStatus[]
}

export interface CreateDefectRequest {
  testResultId: number
  title?: string
  description?: string
  severity?: Severity
}
