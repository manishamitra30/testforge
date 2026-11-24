import type { Priority } from "@/types"

export type RunStatus = "QUEUED" | "RUNNING" | "IN_PROGRESS" | "COMPLETED" | "FAILED" | "ABORTED"
export type ResultStatus = "NOT_RUN" | "PASSED" | "FAILED" | "BLOCKED" | "SKIPPED"

export interface RunSummary {
  total: number
  passed: number
  failed: number
  blocked: number
  skipped: number
  notRun: number
  passRate: number
}

export interface Run {
  id: number
  projectId: number
  suiteId: number
  suiteName: string
  name: string
  type: "MANUAL" | "AUTOMATED"
  status: RunStatus
  startedAt: string
  finishedAt: string | null
  triggeredBy: string | null
  summary: RunSummary
}

export interface RunResult {
  id: number
  testCaseId: number
  title: string
  priority: Priority
  status: ResultStatus
  durationMs: number | null
  comment: string | null
  errorMessage: string | null
  executedBy: string | null
}

export interface RunDetail {
  run: Run
  results: RunResult[]
}

export interface RunLogChunk {
  lines: string[]
  next: number
}

export type RunEvent =
  | { type: "LOG"; runId: number; line: string; offset: number; at: string }
  | { type: "STATUS"; runId: number; status: RunStatus; at: string }