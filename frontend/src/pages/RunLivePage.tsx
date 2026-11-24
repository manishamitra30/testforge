import { useEffect, useRef } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import { getErrorMessage } from "@/api/client"
import { fetchRun } from "@/api/runs"
import { PriorityBadge } from "@/components/badges"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useRunSocket, type SocketState } from "@/hooks/useRunSocket"
import type { ResultStatus, RunStatus } from "@/types/run"

const green = "border-green-200 bg-green-100 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200"
const red = "border-red-200 bg-red-100 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
const amber = "border-amber-200 bg-amber-100 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200"
const blue = "border-blue-200 bg-blue-100 text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200"
const slate = "border-slate-200 bg-slate-100 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200"

const runStyles: Record<RunStatus, string> = {
  QUEUED: slate,
  RUNNING: `${blue} animate-pulse`,
  IN_PROGRESS: blue,
  COMPLETED: green,
  FAILED: red,
  ABORTED: amber,
}

const resultStyles: Record<ResultStatus, string> = {
  PASSED: green,
  FAILED: red,
  BLOCKED: amber,
  SKIPPED: slate,
  NOT_RUN: slate,
}

const connectionText: Record<SocketState, string> = {
  idle: "Not streaming",
  connecting: "Connecting...",
  live: "Live",
  reconnecting: "Reconnecting...",
  error: "Connection error",
}

const connectionDot: Record<SocketState, string> = {
  idle: "bg-slate-400",
  connecting: "bg-amber-400",
  live: "bg-green-500",
  reconnecting: "bg-amber-400",
  error: "bg-red-500",
}

function label(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase().replace("_", " ")
}

function LogPanel({ lines }: { lines: string[] }) {
  const ref = useRef<HTMLPreElement>(null)
  useEffect(() => {
    const element = ref.current
    if (element) element.scrollTop = element.scrollHeight
  }, [lines.length])

  return (
    <pre
      ref={ref}
      className="max-h-96 overflow-auto rounded-md bg-zinc-950 p-3 font-mono text-xs leading-relaxed text-zinc-100"
    >
      {lines.length === 0 ? "Waiting for output..." : lines.join("\n")}
    </pre>
  )
}

function Stat({ title, value }: { title: string; value: string | number }) {
  return (
    <Card>
      <CardContent className="py-4">
        <p className="text-xs text-muted-foreground">{title}</p>
        <p className="text-2xl font-semibold">{value}</p>
      </CardContent>
    </Card>
  )
}

function RunLiveView({ runId }: { runId: number }) {
  const runQuery = useQuery({ queryKey: ["run", runId], queryFn: () => fetchRun(runId) })
  const run = runQuery.data?.run
  const active = run?.status === "QUEUED" || run?.status === "RUNNING" || run?.status === "IN_PROGRESS"
  const { lines, connection, error } = useRunSocket(runId, active)

  if (runQuery.isLoading) return <Skeleton className="h-64 w-full rounded-xl" />
  if (runQuery.isError || !run || !runQuery.data) {
    return (
      <Card>
        <CardContent className="py-6 text-sm text-destructive">{getErrorMessage(runQuery.error)}</CardContent>
      </Card>
    )
  }

  const s = run.summary
  const executed = s.total - s.notRun
  const pct = s.total === 0 ? 0 : Math.round((executed / s.total) * 100)
  const waiting = active && executed === 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{run.name}</h1>
            <Badge variant="outline" className={runStyles[run.status]}>
              {label(run.status)}
            </Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            Suite: {run.suiteName} - {label(run.type)} - triggered by {run.triggeredBy ?? "unknown"}
          </p>
        </div>
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-2 text-sm text-muted-foreground">
            <span className={`size-2 rounded-full ${connectionDot[connection]}`} />
            {connectionText[connection]}
          </span>
          <Link to="/test-runs" className={buttonVariants({ variant: "outline", size: "sm" })}>
            All runs
          </Link>
        </div>
      </div>

      {error && <p className="text-sm text-destructive">{error}</p>}

      <Card>
        <CardContent className="space-y-3 py-4">
          <div className="flex items-center justify-between text-sm">
            <span>
              {executed} of {s.total} results recorded
            </span>
            <span className="text-muted-foreground">Pass rate {s.passRate}%</span>
          </div>
          <div className="h-2 w-full overflow-hidden rounded-full bg-muted">
            <div
              className={`h-full bg-primary transition-all ${waiting ? "animate-pulse" : ""}`}
              style={{ width: waiting ? "100%" : `${pct}%` }}
            />
          </div>
          {waiting && (
            <p className="text-xs text-muted-foreground">
              Results are imported from the Cucumber report when the suite finishes. Follow the live output below.
            </p>
          )}
        </CardContent>
      </Card>

      <div className="grid grid-cols-2 gap-3 sm:grid-cols-5">
        <Stat title="Passed" value={s.passed} />
        <Stat title="Failed" value={s.failed} />
        <Stat title="Blocked" value={s.blocked} />
        <Stat title="Skipped" value={s.skipped} />
        <Stat title="Not run" value={s.notRun} />
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Live output</CardTitle>
        </CardHeader>
        <CardContent>
          <LogPanel lines={lines} />
        </CardContent>
      </Card>

      <div className="rounded-xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Test case</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Duration</TableHead>
              <TableHead>Message</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {runQuery.data.results.map((result) => (
              <TableRow key={result.id}>
                <TableCell className="font-medium">{result.title}</TableCell>
                <TableCell>
                  <PriorityBadge priority={result.priority} />
                </TableCell>
                <TableCell>
                  <Badge variant="outline" className={resultStyles[result.status]}>
                    {label(result.status)}
                  </Badge>
                </TableCell>
                <TableCell className="text-muted-foreground">
                  {result.durationMs ? `${(result.durationMs / 1000).toFixed(1)}s` : "-"}
                </TableCell>
                <TableCell className="max-w-md truncate text-muted-foreground" title={result.errorMessage ?? ""}>
                  {result.errorMessage ?? result.comment ?? "-"}
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}

export default function RunLivePage() {
  const { id } = useParams<{ id: string }>()
  const runId = Number(id)
  if (!Number.isInteger(runId) || runId <= 0) {
    return <p className="text-sm text-destructive">Invalid run id.</p>
  }
  return <RunLiveView key={runId} runId={runId} />
}