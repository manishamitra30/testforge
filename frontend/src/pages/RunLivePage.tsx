import { useEffect, useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { Link, useParams } from "react-router-dom"
import { toast } from "sonner"
import { getErrorMessage } from "@/api/client"
import { abortRun, fetchRun } from "@/api/runs"
import { RunStatusBadge } from "@/components/RunBadges"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import LogDefectDialog from "@/features/runs/LogDefectDialog"
import ResultRow from "@/features/runs/ResultRow"
import { useRunSocket, type SocketState } from "@/hooks/useRunSocket"
import type { RunResult } from "@/types/run"

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
  const queryClient = useQueryClient()
  const [defectFor, setDefectFor] = useState<RunResult | null>(null)
  const run = runQuery.data?.run
  const active = run?.status === "QUEUED" || run?.status === "RUNNING" || run?.status === "IN_PROGRESS"
  const manual = run?.type === "MANUAL"
  const { lines, connection, error } = useRunSocket(runId, active && !manual)

  const abort = useMutation({
    mutationFn: () => abortRun(runId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["run", runId] })
      queryClient.invalidateQueries({ queryKey: ["runs"] })
      toast.success("Run aborted")
    },
    onError: (e) => toast.error(getErrorMessage(e)),
  })

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
  const editable = manual && (run.status === "IN_PROGRESS" || run.status === "COMPLETED")
  const waiting = active && !manual && executed === 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold">{run.name}</h1>
            <RunStatusBadge status={run.status} />
          </div>
          <p className="text-sm text-muted-foreground">
            Suite: {run.suiteName} - {label(run.type)} - triggered by {run.triggeredBy ?? "unknown"}
          </p>
        </div>
        <div className="flex items-center gap-3">
          {!manual && (
            <span className="flex items-center gap-2 text-sm text-muted-foreground">
              <span className={`size-2 rounded-full ${connectionDot[connection]}`} />
              {connectionText[connection]}
            </span>
          )}
          {manual && run.status === "IN_PROGRESS" && (
            <Button
              variant="outline"
              size="sm"
              disabled={abort.isPending}
              onClick={() => window.confirm("Abort this run? Its results can no longer be changed.") && abort.mutate()}
            >
              Abort run
            </Button>
          )}
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

      {!manual && (
        <Card>
          <CardHeader>
            <CardTitle>Live output</CardTitle>
          </CardHeader>
          <CardContent>
            <LogPanel lines={lines} />
          </CardContent>
        </Card>
      )}

      <div className="rounded-xl border bg-background">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Test case</TableHead>
              <TableHead>Priority</TableHead>
              <TableHead>Status</TableHead>
              {editable ? (
                <>
                  <TableHead>Record result</TableHead>
                  <TableHead>Comment</TableHead>
                </>
              ) : (
                <>
                  <TableHead>Duration</TableHead>
                  <TableHead>Message</TableHead>
                </>
              )}
              <TableHead className="text-right" />
            </TableRow>
          </TableHeader>
          <TableBody>
            {runQuery.data.results.map((result) => (
              <ResultRow
                key={`${result.id}-${result.status}-${result.comment ?? ""}`}
                runId={runId}
                result={result}
                editable={editable}
                onLogDefect={setDefectFor}
              />
            ))}
          </TableBody>
        </Table>
      </div>

      <LogDefectDialog result={defectFor} onClose={() => setDefectFor(null)} />
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