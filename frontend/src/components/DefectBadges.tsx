import { Badge } from "@/components/ui/badge"
import type { Severity } from "@/types/dashboard"
import type { DefectStatus } from "@/types/defect"

const severityStyles: Record<Severity, string> = {
  CRITICAL: "border-red-200 bg-red-100 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200",
  HIGH: "border-orange-200 bg-orange-100 text-orange-800 dark:border-orange-900 dark:bg-orange-950 dark:text-orange-200",
  MEDIUM: "border-amber-200 bg-amber-100 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200",
  LOW: "border-green-200 bg-green-100 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200",
}

const statusStyles: Record<DefectStatus, string> = {
  OPEN: "border-red-200 bg-red-100 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200",
  IN_PROGRESS: "border-blue-200 bg-blue-100 text-blue-800 dark:border-blue-900 dark:bg-blue-950 dark:text-blue-200",
  RESOLVED: "border-green-200 bg-green-100 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200",
  CLOSED: "border-slate-200 bg-slate-100 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200",
}

export const defectStatusLabel: Record<DefectStatus, string> = {
  OPEN: "Open",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
}

export function SeverityBadge({ severity }: { severity: Severity }) {
  return (
    <Badge variant="outline" className={severityStyles[severity]}>
      {severity.charAt(0) + severity.slice(1).toLowerCase()}
    </Badge>
  )
}

export function DefectStatusBadge({ status }: { status: DefectStatus }) {
  return (
    <Badge variant="outline" className={statusStyles[status]}>
      {defectStatusLabel[status]}
    </Badge>
  )
}
