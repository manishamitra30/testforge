import { useState } from "react"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { Link, useNavigate } from "react-router-dom"
import { FolderKanban, Play } from "lucide-react"
import { getErrorMessage } from "@/api/client"
import { fetchRuns } from "@/api/runs"
import { RunStatusBadge } from "@/components/RunBadges"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import NativeSelect from "@/components/NativeSelect"
import ProjectPicker from "@/features/dashboard/ProjectPicker"
import { useSelectedProject } from "@/hooks/useSelectedProject"
import type { RunStatus } from "@/types/run"

const PAGE_SIZE = 10

function formatDate(iso: string) {
  return new Date(iso).toLocaleString(undefined, {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  })
}

export default function TestRunsPage() {
  const navigate = useNavigate()
  const { projects, projectId, selectProject, isLoading } = useSelectedProject()
  const [page, setPage] = useState(0)
  const [status, setStatus] = useState<RunStatus | "ALL">("ALL")

  const runsQuery = useQuery({
    queryKey: ["runs", projectId, page],
    queryFn: () => fetchRuns(projectId as number, page, PAGE_SIZE),
    enabled: projectId !== null,
    placeholderData: keepPreviousData,
  })

  if (isLoading) return <Skeleton className="h-64 w-full rounded-xl" />

  if (projectId === null) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
          <FolderKanban className="size-10 text-muted-foreground" />
          <p className="font-medium">Create a project first</p>
          <Link to="/projects" className={buttonVariants()}>
            Go to projects
          </Link>
        </CardContent>
      </Card>
    )
  }

  const allRuns = runsQuery.data?.content ?? []
  const runs = status === "ALL" ? allRuns : allRuns.filter((run) => run.status === status)
  const totalPages = runsQuery.data?.totalPages ?? 0

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Test Runs</h1>
          <p className="text-sm text-muted-foreground">Every run for the selected project, newest first.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <NativeSelect
            className="w-40"
            aria-label="Filter by status"
            value={status}
            onChange={(event) => setStatus(event.target.value as RunStatus | "ALL")}
          >
            <option value="ALL">All statuses</option>
            <option value="IN_PROGRESS">In progress</option>
            <option value="RUNNING">Running</option>
            <option value="COMPLETED">Completed</option>
            <option value="FAILED">Failed</option>
            <option value="ABORTED">Aborted</option>
          </NativeSelect>
          <ProjectPicker
            projects={projects}
            projectId={projectId}
            onChange={(id) => {
              setPage(0)
              selectProject(id)
            }}
          />
        </div>
      </div>

      {runsQuery.isError && <p className="text-sm text-destructive">{getErrorMessage(runsQuery.error)}</p>}

      {runsQuery.isLoading ? (
        <Skeleton className="h-64 rounded-xl" />
      ) : runs.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <Play className="size-10 text-muted-foreground" />
            <p className="font-medium">No runs to show</p>
            <p className="text-sm text-muted-foreground">
              Runs appear here once you start one from a suite or the automation engine.
            </p>
          </CardContent>
        </Card>
      ) : (
        <Card>
          <CardContent className="p-0">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Run</TableHead>
                  <TableHead>Suite</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="w-44">Progress</TableHead>
                  <TableHead className="text-right">Pass rate</TableHead>
                  <TableHead>Started</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {runs.map((run) => {
                  const { total, notRun, failed, passed } = run.summary
                  const done = total - notRun
                  const donePct = total === 0 ? 0 : (done / total) * 100
                  return (
                    <TableRow
                      key={run.id}
                      className="cursor-pointer"
                      onClick={() => navigate(`/runs/${run.id}`)}
                    >
                      <TableCell className="font-medium">
                        <Link to={`/runs/${run.id}`} className="hover:underline" onClick={(e) => e.stopPropagation()}>
                          {run.name}
                        </Link>
                      </TableCell>
                      <TableCell>{run.suiteName}</TableCell>
                      <TableCell className="capitalize">{run.type.toLowerCase()}</TableCell>
                      <TableCell>
                        <RunStatusBadge status={run.status} />
                      </TableCell>
                      <TableCell>
                        <div
                          className="flex h-2 overflow-hidden rounded-full bg-muted"
                          title={`${passed} passed, ${failed} failed, ${notRun} not run`}
                        >
                          <div className="bg-green-500" style={{ width: `${total ? (passed / total) * 100 : 0}%` }} />
                          <div className="bg-red-500" style={{ width: `${total ? (failed / total) * 100 : 0}%` }} />
                        </div>
                        <p className="mt-1 text-xs text-muted-foreground">
                          {done}/{total} executed ({Math.round(donePct)}%)
                        </p>
                      </TableCell>
                      <TableCell className="text-right tabular-nums">{Math.round(run.summary.passRate)}%</TableCell>
                      <TableCell className="text-sm text-muted-foreground">{formatDate(run.startedAt)}</TableCell>
                    </TableRow>
                  )
                })}
              </TableBody>
            </Table>
          </CardContent>
        </Card>
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-2 text-sm">
          <button
            className={buttonVariants({ variant: "outline", size: "sm" })}
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Previous
          </button>
          <span className="text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <button
            className={buttonVariants({ variant: "outline", size: "sm" })}
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </button>
        </div>
      )}
    </div>
  )
}
