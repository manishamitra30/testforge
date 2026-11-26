import { Badge } from "@/components/ui/badge"
import type { ResultStatus, RunStatus } from "@/types/run"

const green = "border-green-200 bg-green-100 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200"
const red = "border-red-200 bg-red-100 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
const amber = "border-amber-200 bg-amber-100 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
const gray = "border-slate-200 bg-slate-100 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"
const blue = "border-blue-200 bg-blue-100 text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200"

const resultStyles: Record<ResultStatus, string> = {
  PASSED: green,
  FAILED: red,
  BLOCKED: amber,
  SKIPPED: gray,
  NOT_RUN: gray,
}

const runStyles: Record<RunStatus, string> = {
  COMPLETED: green,
  FAILED: red,
  ABORTED: amber,
  QUEUED: gray,
  RUNNING: blue,
  IN_PROGRESS: blue,
}

function label(value: string) {
  const text = value.replace("_", " ").toLowerCase()
  return text.charAt(0).toUpperCase() + text.slice(1)
}

export function ResultBadge({ status }: { status: ResultStatus }) {
  return (
    <Badge variant="outline" className={resultStyles[status]}>
      {label(status)}
    </Badge>
  )
}

export function RunStatusBadge({ status }: { status: RunStatus }) {
  const live = status === "RUNNING" || status === "IN_PROGRESS"
  return (
    <Badge variant="outline" className={runStyles[status]}>
      {live && <span className="mr-1.5 inline-block size-1.5 animate-pulse rounded-full bg-current" />}
      {label(status)}
    </Badge>
  )
}
