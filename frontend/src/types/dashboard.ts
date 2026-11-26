export type Severity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"

export interface DashboardSummary {
  totalCases: number
  totalRuns: number
  passRate: number
  openDefects: number
}

export interface PassRatePoint {
  date: string
  passed: number
  executed: number
  passRate: number
}

export interface SeverityCount {
  severity: Severity
  count: number
}

export interface Activity {
  type: "RUN" | "DEFECT" | string
  refId: number
  title: string
  detail: string
  at: string
}

export interface FlakyTest {
  testCaseId: number
  title: string
  executions: number
  failures: number
  flips: number
  flakiness: number
}
