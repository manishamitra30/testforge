import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { ChevronDown, ChevronRight, FolderKanban, Layers } from "lucide-react"
import { fetchCases } from "@/api/cases"
import { getErrorMessage } from "@/api/client"
import { fetchSuites } from "@/api/projects"
import { PriorityBadge, StatusBadge } from "@/components/badges"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import NewSuiteDialog from "@/features/cases/NewSuiteDialog"
import { useSelectedProject } from "@/hooks/useSelectedProject"
import type { TestCaseSummary } from "@/types"

export default function SuitesPage() {
  const { projectId, isLoading } = useSelectedProject()
  const [open, setOpen] = useState<Set<number>>(() => new Set())
  const enabled = projectId !== null

  const suitesQuery = useQuery({
    queryKey: ["suites", projectId],
    queryFn: () => fetchSuites(projectId as number),
    enabled,
  })
  const casesQuery = useQuery({
    queryKey: ["cases", projectId, "all-for-suites"],
    queryFn: () => fetchCases({ projectId: projectId as number, page: 0, size: 200 }),
    enabled,
  })

  function toggle(id: number) {
    setOpen((previous) => {
      const next = new Set(previous)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
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

  const suites = suitesQuery.data?.content ?? []
  const bySuite = new Map<number, TestCaseSummary[]>()
  for (const testCase of casesQuery.data?.content ?? []) {
    bySuite.set(testCase.suiteId, [...(bySuite.get(testCase.suiteId) ?? []), testCase])
  }
  const error = suitesQuery.error ?? casesQuery.error

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Test Suites</h1>
          <p className="text-sm text-muted-foreground">Test cases grouped by suite for the selected project.</p>
        </div>
        <NewSuiteDialog projectId={projectId} />
      </div>

      {error && <p className="text-sm text-destructive">{getErrorMessage(error)}</p>}

      {suitesQuery.isLoading || casesQuery.isLoading ? (
        <Skeleton className="h-64 rounded-xl" />
      ) : suites.length === 0 ? (
        <Card>
          <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
            <Layers className="size-10 text-muted-foreground" />
            <p className="font-medium">No suites yet</p>
            <p className="text-sm text-muted-foreground">Create a suite to start grouping test cases.</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {suites.map((suite) => {
            const cases = bySuite.get(suite.id) ?? []
            const expanded = open.has(suite.id)
            return (
              <Card key={suite.id}>
                <button
                  type="button"
                  className="flex w-full items-center gap-3 px-4 py-3 text-left"
                  aria-expanded={expanded}
                  onClick={() => toggle(suite.id)}
                >
                  {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
                  <div className="min-w-0 flex-1">
                    <p className="font-medium">{suite.name}</p>
                    {suite.description && (
                      <p className="truncate text-sm text-muted-foreground">{suite.description}</p>
                    )}
                  </div>
                  <span className="rounded-full bg-muted px-2.5 py-0.5 text-xs tabular-nums text-muted-foreground">
                    {cases.length} {cases.length === 1 ? "case" : "cases"}
                  </span>
                </button>
                {expanded && (
                  <CardContent className="border-t pt-3">
                    {cases.length === 0 ? (
                      <p className="py-2 text-sm text-muted-foreground">
                        No test cases in this suite yet. Add some from{" "}
                        <Link to={`/test-cases?project=${projectId}`} className="underline">
                          Test Cases
                        </Link>
                        .
                      </p>
                    ) : (
                      <ul className="divide-y">
                        {cases.map((testCase) => (
                          <li key={testCase.id} className="flex flex-wrap items-center gap-3 py-2 text-sm">
                            <span className="min-w-0 flex-1 font-medium">{testCase.title}</span>
                            {testCase.tags && (
                              <span className="text-xs text-muted-foreground">{testCase.tags}</span>
                            )}
                            <PriorityBadge priority={testCase.priority} />
                            <StatusBadge status={testCase.status} />
                          </li>
                        ))}
                      </ul>
                    )}
                  </CardContent>
                )}
              </Card>
            )
          })}
        </div>
      )}
    </div>
  )
}
