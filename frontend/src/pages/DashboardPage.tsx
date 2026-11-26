import { useQuery } from "@tanstack/react-query"
import { Link } from "react-router-dom"
import { Activity as ActivityIcon, Bug, FolderKanban, ListChecks, Percent, Play } from "lucide-react"
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts"
import {
  fetchDefectsBySeverity,
  fetchFlakyTests,
  fetchPassRateTrend,
  fetchRecentActivity,
  fetchSummary,
} from "@/api/dashboard"
import { fetchRuns } from "@/api/runs"
import { buttonVariants } from "@/components/ui/button"
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import { useSelectedProject } from "@/hooks/useSelectedProject"
import type { Run } from "@/types/run"
import type { Severity } from "@/types/dashboard"

const PRIMARY = "#4f46e5"
const SEVERITY_COLORS: Record<Severity, string> = {
  CRITICAL: "#dc2626",
  HIGH: "#f97316",
  MEDIUM: "#f59e0b",
  LOW: "#16a34a",
}

function percent(value: number) {
  return `${Math.round(value)}%`
}

function shortDate(iso: string) {
  return new Date(iso).toLocaleDateString(undefined, { day: "numeric", month: "short" })
}

function timeAgo(iso: string) {
  const minutes = Math.max(0, Math.round((Date.now() - new Date(iso).getTime()) / 60000))
  if (minutes < 1) return "just now"
  if (minutes < 60) return `${minutes} min ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours} h ago`
  return `${Math.round(hours / 24)} d ago`
}

/** Monday of the week containing the date, as a stable key. */
function weekStart(date: Date) {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  d.setDate(d.getDate() - ((d.getDay() + 6) % 7))
  return d
}

function runsPerWeek(runs: Run[], weeks = 8) {
  const current = weekStart(new Date())
  const buckets = Array.from({ length: weeks }, (_, i) => {
    const start = new Date(current)
    start.setDate(start.getDate() - (weeks - 1 - i) * 7)
    return { start, label: start.toLocaleDateString(undefined, { day: "numeric", month: "short" }), runs: 0 }
  })
  for (const run of runs) {
    const started = weekStart(new Date(run.startedAt)).getTime()
    const bucket = buckets.find((b) => b.start.getTime() === started)
    if (bucket) bucket.runs += 1
  }
  return buckets
}

function ChartCard({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  )
}

function Empty({ text }: { text: string }) {
  return <p className="py-10 text-center text-sm text-muted-foreground">{text}</p>
}

function Kpi({ label, value, icon: Icon }: { label: string; value: string; icon: typeof Percent }) {
  return (
    <Card>
      <CardContent className="flex items-center justify-between py-5">
        <div>
          <p className="text-sm text-muted-foreground">{label}</p>
          <p className="text-3xl font-semibold tabular-nums">{value}</p>
        </div>
        <div className="flex size-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
          <Icon className="size-5" />
        </div>
      </CardContent>
    </Card>
  )
}

export default function DashboardPage() {
  const { projectId, isLoading } = useSelectedProject()
  const enabled = projectId !== null
  const id = projectId as number

  const summary = useQuery({ queryKey: ["dash", "summary", projectId], queryFn: () => fetchSummary(id), enabled })
  const trend = useQuery({ queryKey: ["dash", "trend", projectId], queryFn: () => fetchPassRateTrend(id, 30), enabled })
  const severity = useQuery({ queryKey: ["dash", "severity", projectId], queryFn: () => fetchDefectsBySeverity(id), enabled })
  const activity = useQuery({ queryKey: ["dash", "activity", projectId], queryFn: () => fetchRecentActivity(id, 10), enabled })
  const flaky = useQuery({ queryKey: ["dash", "flaky", projectId], queryFn: () => fetchFlakyTests(id, 10), enabled })
  const runs = useQuery({ queryKey: ["runs", projectId], queryFn: () => fetchRuns(id), enabled })

  if (isLoading) return <Skeleton className="h-64 w-full rounded-xl" />

  if (projectId === null) {
    return (
      <Card>
        <CardContent className="flex flex-col items-center gap-3 py-12 text-center">
          <FolderKanban className="size-10 text-muted-foreground" />
          <p className="font-medium">Create a project to see its dashboard</p>
          <Link to="/projects" className={buttonVariants()}>
            Go to projects
          </Link>
        </CardContent>
      </Card>
    )
  }

  const weekly = runsPerWeek(runs.data?.content ?? [])
  const runsThisWeek = weekly[weekly.length - 1]?.runs ?? 0
  const trendData = (trend.data ?? []).map((p) => ({ ...p, label: shortDate(p.date) }))
  const severityData = (severity.data ?? []).filter((s) => s.count > 0)
  const failed = summary.isError || trend.isError

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold">Dashboard</h1>
          <p className="text-sm text-muted-foreground">Quality at a glance for the selected project.</p>
        </div>
      </div>

      {failed && (
        <p className="text-sm text-destructive">
          Could not load some dashboard data. Check that the backend is running.
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {summary.isLoading ? (
          Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-24 rounded-xl" />)
        ) : (
          <>
            <Kpi label="Pass rate" value={percent(summary.data?.passRate ?? 0)} icon={Percent} />
            <Kpi label="Total cases" value={String(summary.data?.totalCases ?? 0)} icon={ListChecks} />
            <Kpi label="Runs this week" value={String(runsThisWeek)} icon={Play} />
            <Kpi label="Open defects" value={String(summary.data?.openDefects ?? 0)} icon={Bug} />
          </>
        )}
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <div className="lg:col-span-2">
          <ChartCard title="Pass rate, last 30 days">
            {trend.isLoading ? (
              <Skeleton className="h-64" />
            ) : trendData.length === 0 ? (
              <Empty text="No executed test results yet. Complete a run to see the trend." />
            ) : (
              <div className="h-64">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart data={trendData} margin={{ top: 8, right: 16, bottom: 0, left: -16 }}>
                    <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.3} />
                    <XAxis dataKey="label" fontSize={12} tickLine={false} />
                    <YAxis domain={[0, 100]} unit="%" fontSize={12} tickLine={false} />
                    <Tooltip formatter={(value) => [`${Math.round(Number(value))}%`, "Pass rate"]} />
                    <Line type="monotone" dataKey="passRate" stroke={PRIMARY} strokeWidth={2} dot={{ r: 3 }} />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            )}
          </ChartCard>
        </div>

        <ChartCard title="Defects by severity">
          {severity.isLoading ? (
            <Skeleton className="h-64" />
          ) : severityData.length === 0 ? (
            <Empty text="No defects logged. Nice." />
          ) : (
            <div className="h-64">
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie data={severityData} dataKey="count" nameKey="severity" innerRadius={55} outerRadius={85} paddingAngle={2}>
                    {severityData.map((entry) => (
                      <Cell key={entry.severity} fill={SEVERITY_COLORS[entry.severity]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
              <div className="-mt-1 flex flex-wrap justify-center gap-x-3 gap-y-1 text-xs">
                {severityData.map((entry) => (
                  <span key={entry.severity} className="flex items-center gap-1">
                    <span className="size-2 rounded-full" style={{ background: SEVERITY_COLORS[entry.severity] }} />
                    {entry.severity.charAt(0) + entry.severity.slice(1).toLowerCase()} ({entry.count})
                  </span>
                ))}
              </div>
            </div>
          )}
        </ChartCard>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <ChartCard title="Runs per week">
          {runs.isLoading ? (
            <Skeleton className="h-56" />
          ) : (
            <div className="h-56">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={weekly} margin={{ top: 8, right: 16, bottom: 0, left: -24 }}>
                  <CartesianGrid strokeDasharray="3 3" strokeOpacity={0.3} vertical={false} />
                  <XAxis dataKey="label" fontSize={12} tickLine={false} />
                  <YAxis allowDecimals={false} fontSize={12} tickLine={false} />
                  <Tooltip formatter={(value) => [value, "Runs"]} />
                  <Bar dataKey="runs" fill={PRIMARY} radius={[4, 4, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </ChartCard>

        <ChartCard title="Flaky tests">
          {flaky.isLoading ? (
            <Skeleton className="h-56" />
          ) : (flaky.data ?? []).length === 0 ? (
            <Empty text="No flaky tests detected in the last 10 runs." />
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Test</TableHead>
                  <TableHead className="text-right">Runs</TableHead>
                  <TableHead className="text-right">Failed</TableHead>
                  <TableHead className="text-right">Flips</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(flaky.data ?? []).map((test) => (
                  <TableRow key={test.testCaseId}>
                    <TableCell className="max-w-56 truncate font-medium">{test.title}</TableCell>
                    <TableCell className="text-right tabular-nums">{test.executions}</TableCell>
                    <TableCell className="text-right tabular-nums">{test.failures}</TableCell>
                    <TableCell className="text-right tabular-nums">{test.flips}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </ChartCard>
      </div>

      <ChartCard title="Recent activity">
        {activity.isLoading ? (
          <Skeleton className="h-40" />
        ) : (activity.data ?? []).length === 0 ? (
          <Empty text="Nothing has happened yet. Create a run or log a defect." />
        ) : (
          <ul className="divide-y">
            {(activity.data ?? []).map((item) => (
              <li key={`${item.type}-${item.refId}`} className="flex items-center gap-3 py-2.5 text-sm">
                <div className="flex size-8 shrink-0 items-center justify-center rounded-full bg-muted">
                  {item.type === "DEFECT" ? <Bug className="size-4" /> : <ActivityIcon className="size-4" />}
                </div>
                <div className="min-w-0 flex-1">
                  {item.type === "RUN" ? (
                    <Link to={`/runs/${item.refId}`} className="font-medium hover:underline">
                      {item.title}
                    </Link>
                  ) : (
                    <span className="font-medium">{item.title}</span>
                  )}
                  <p className="truncate text-xs text-muted-foreground">{item.detail}</p>
                </div>
                <span className="shrink-0 text-xs text-muted-foreground">{timeAgo(item.at)}</span>
              </li>
            ))}
          </ul>
        )}
      </ChartCard>
    </div>
  )
}

