import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { Bug, FolderKanban, LayoutGrid, Table2 } from "lucide-react"
import { getErrorMessage } from "@/api/client"
import { fetchDefects } from "@/api/defects"
import NativeSelect from "@/components/NativeSelect"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import DefectBoard from "@/features/defects/DefectBoard"
import DefectTable from "@/features/defects/DefectTable"
import { useChangeDefectStatus } from "@/features/defects/useChangeDefectStatus"
import { useSelectedProject } from "@/hooks/useSelectedProject"
import type { Severity } from "@/types/dashboard"
import type { DefectStatus } from "@/types/defect"

type View = "board" | "table"
const VIEW_KEY = "testforge.defects.view"

function initialView(): View {
  try {
    return localStorage.getItem(VIEW_KEY) === "table" ? "table" : "board"
  } catch {
    return "board"
  }
}

export default function DefectsPage() {
  const { projectId, isLoading } = useSelectedProject()
  const [view, setView] = useState<View>(initialView)
  const [severity, setSeverity] = useState<Severity | "ALL">("ALL")
  const severityFilter = severity === "ALL" ? undefined : severity
  const queryKey = ["defects", projectId, severityFilter] as const

  const defectsQuery = useQuery({
    queryKey,
    queryFn: () => fetchDefects(projectId as number, severityFilter),
    enabled: projectId !== null,
  })
  const move = useChangeDefectStatus(queryKey)

  function changeView(next: View) {
    setView(next)
    try {
      localStorage.setItem(VIEW_KEY, next)
    } catch {
      // the view just won't be remembered
    }
  }

  function handleMove(id: number, status: DefectStatus) {
    move.mutate({ id, status })
  }

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

  const defects = defectsQuery.data?.content ?? []

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Defects</h1>
          <p className="text-sm text-muted-foreground">
            {view === "board"
              ? "Drag a card to another column to change its status."
              : "Use the Move to menu to change a defect's status."}
          </p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <div className="flex rounded-md border p-0.5" role="group" aria-label="View">
            <Button
              size="sm"
              variant={view === "board" ? "secondary" : "ghost"}
              aria-pressed={view === "board"}
              onClick={() => changeView("board")}
            >
              <LayoutGrid className="mr-1 size-4" />
              Board
            </Button>
            <Button
              size="sm"
              variant={view === "table" ? "secondary" : "ghost"}
              aria-pressed={view === "table"}
              onClick={() => changeView("table")}
            >
              <Table2 className="mr-1 size-4" />
              Table
            </Button>
          </div>
          <NativeSelect
            className="w-40"
            aria-label="Filter by severity"
            value={severity}
            onChange={(event) => setSeverity(event.target.value as Severity | "ALL")}
          >
            <option value="ALL">All severities</option>
            <option value="CRITICAL">Critical</option>
            <option value="HIGH">High</option>
            <option value="MEDIUM">Medium</option>
            <option value="LOW">Low</option>
          </NativeSelect>
        </div>
      </div>

      {defectsQuery.isError && <p className="text-sm text-destructive">{getErrorMessage(defectsQuery.error)}</p>}

      {defectsQuery.isLoading ? (
        <Skeleton className="h-64 rounded-xl" />
      ) : defects.length === 0 && severity === "ALL" ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <Bug className="size-10 text-muted-foreground" />
            <p className="font-medium">No defects yet</p>
            <p className="max-w-sm text-sm text-muted-foreground">
              Defects are logged from a failed result on a test run.
            </p>
            <Link to="/test-runs" className={buttonVariants({ variant: "outline" })}>
              Go to test runs
            </Link>
          </CardContent>
        </Card>
      ) : view === "board" ? (
        <DefectBoard defects={defects} onMove={handleMove} />
      ) : defects.length === 0 ? (
        <p className="py-8 text-center text-sm text-muted-foreground">No defects match this filter.</p>
      ) : (
        <DefectTable defects={defects} onMove={handleMove} />
      )}
    </div>
  )
}
