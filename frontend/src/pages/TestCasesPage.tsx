import { useState } from "react"
import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { Link, useSearchParams } from "react-router-dom"
import { ListChecks, Plus, Search } from "lucide-react"
import { fetchCases } from "@/api/cases"
import { getErrorMessage } from "@/api/client"
import { fetchProjects, fetchSuites } from "@/api/projects"
import { PriorityBadge, StatusBadge } from "@/components/badges"
import NativeSelect from "@/components/NativeSelect"
import { Button, buttonVariants } from "@/components/ui/button"
import { Card, CardContent } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import NewSuiteDialog from "@/features/cases/NewSuiteDialog"
import TestCaseSheet from "@/features/cases/TestCaseSheet"
import { useDebounce } from "@/hooks/useDebounce"
import type { Priority, TestCaseStatus } from "@/types"

const PAGE_SIZE = 10

export default function TestCasesPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [search, setSearch] = useState("")
  const [priority, setPriority] = useState<Priority | "ALL">("ALL")
  const [status, setStatus] = useState<TestCaseStatus | "ALL">("ALL")
  const [page, setPage] = useState(0)
  const [sheetOpen, setSheetOpen] = useState(false)
  const debouncedSearch = useDebounce(search)

  const projectsQuery = useQuery({ queryKey: ["projects"], queryFn: fetchProjects })
  const projects = projectsQuery.data?.content ?? []
  const paramId = Number(searchParams.get("project"))
  const projectId = projects.some((p) => p.id === paramId) ? paramId : (projects[0]?.id ?? null)

  const suitesQuery = useQuery({
    queryKey: ["suites", projectId],
    queryFn: () => fetchSuites(projectId as number),
    enabled: projectId !== null,
  })
  const suites = suitesQuery.data?.content ?? []
  const suiteNames = new Map<number, string>(suites.map((suite) => [suite.id, suite.name]))

  const casesQuery = useQuery({
    queryKey: ["cases", projectId, debouncedSearch, priority, status, page],
    queryFn: () =>
      fetchCases({
        projectId: projectId as number,
        q: debouncedSearch.trim() || undefined,
        priority: priority === "ALL" ? undefined : priority,
        status: status === "ALL" ? undefined : status,
        page,
        size: PAGE_SIZE,
      }),
    enabled: projectId !== null,
    placeholderData: keepPreviousData,
  })
  const cases = casesQuery.data?.content ?? []
  const totalPages = casesQuery.data?.totalPages ?? 0

  function changeProject(value: string) {
    setSearchParams({ project: value })
    setPage(0)
  }

  if (projectsQuery.isLoading) {
    return <Skeleton className="h-64 w-full rounded-xl" />
  }

  if (projectId === null) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
          <ListChecks className="size-10 text-muted-foreground" />
          <p className="font-medium">Create a project first</p>
          <Link to="/projects" className={buttonVariants()}>
            Go to projects
          </Link>
        </CardContent>
      </Card>
    )
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Test Cases</h1>
          <p className="text-sm text-muted-foreground">Search, filter and add test cases.</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <NativeSelect
            className="w-48"
            value={String(projectId)}
            onChange={(event) => changeProject(event.target.value)}
          >
            {projects.map((project) => (
              <option key={project.id} value={String(project.id)}>
                {project.name}
              </option>
            ))}
          </NativeSelect>
          <NewSuiteDialog projectId={projectId} />
          <Button onClick={() => setSheetOpen(true)} disabled={suites.length === 0}>
            <Plus className="mr-1 size-4" />
            New Test Case
          </Button>
        </div>
      </div>

      {suitesQuery.isSuccess && suites.length === 0 && (
        <p className="text-sm text-muted-foreground">
          This project has no suites yet. Create a suite first, then add test cases to it.
        </p>
      )}

      <div className="flex flex-wrap gap-3">
        <div className="relative min-w-60 flex-1">
          <Search className="absolute left-3 top-2.5 size-4 text-muted-foreground" />
          <Input
            className="pl-9"
            placeholder="Search by title or tag"
            value={search}
            onChange={(event) => {
              setSearch(event.target.value)
              setPage(0)
            }}
          />
        </div>
        <NativeSelect
          className="w-40"
          value={priority}
          onChange={(event) => {
            setPriority(event.target.value as Priority | "ALL")
            setPage(0)
          }}
        >
          <option value="ALL">All priorities</option>
          <option value="CRITICAL">Critical</option>
          <option value="HIGH">High</option>
          <option value="MEDIUM">Medium</option>
          <option value="LOW">Low</option>
        </NativeSelect>
        <NativeSelect
          className="w-40"
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as TestCaseStatus | "ALL")
            setPage(0)
          }}
        >
          <option value="ALL">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="READY">Ready</option>
          <option value="DEPRECATED">Deprecated</option>
        </NativeSelect>
      </div>

      {casesQuery.isError && (
        <Card>
          <CardContent className="flex items-center justify-between py-6">
            <p className="text-sm text-destructive">{getErrorMessage(casesQuery.error)}</p>
            <Button variant="outline" size="sm" onClick={() => casesQuery.refetch()}>
              Retry
            </Button>
          </CardContent>
        </Card>
      )}

      {casesQuery.isLoading ? (
        <Skeleton className="h-64 w-full rounded-xl" />
      ) : (
        !casesQuery.isError && (
          <div className="rounded-xl border bg-background">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Title</TableHead>
                  <TableHead>Suite</TableHead>
                  <TableHead>Priority</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Tags</TableHead>
                  <TableHead>Updated</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {cases.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} className="py-12 text-center text-muted-foreground">
                      No test cases found.
                    </TableCell>
                  </TableRow>
                ) : (
                  cases.map((testCase) => (
                    <TableRow key={testCase.id}>
                      <TableCell className="font-medium">{testCase.title}</TableCell>
                      <TableCell>{suiteNames.get(testCase.suiteId) ?? `#${testCase.suiteId}`}</TableCell>
                      <TableCell>
                        <PriorityBadge priority={testCase.priority} />
                      </TableCell>
                      <TableCell>
                        <StatusBadge status={testCase.status} />
                      </TableCell>
                      <TableCell className="text-muted-foreground">{testCase.tags ?? "-"}</TableCell>
                      <TableCell className="text-muted-foreground">
                        {new Date(testCase.updatedAt).toLocaleDateString()}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        )
      )}

      {totalPages > 1 && (
        <div className="flex items-center justify-end gap-3">
          <span className="text-sm text-muted-foreground">
            Page {page + 1} of {totalPages}
          </span>
          <Button variant="outline" size="sm" disabled={page === 0} onClick={() => setPage(page - 1)}>
            Previous
          </Button>
          <Button
            variant="outline"
            size="sm"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage(page + 1)}
          >
            Next
          </Button>
        </div>
      )}

      <TestCaseSheet
        open={sheetOpen}
        onOpenChange={setSheetOpen}
        suites={suites}
        defaultSuiteId={suites[0]?.id ?? null}
      />
    </div>
  )
}