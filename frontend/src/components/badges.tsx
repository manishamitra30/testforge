import { Badge } from "@/components/ui/badge"
import type { Priority, TestCaseStatus } from "@/types"

const priorityStyles: Record<Priority, string> = {
  CRITICAL: "border-red-200 bg-red-100 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200",
  HIGH: "border-orange-200 bg-orange-100 text-orange-800 dark:border-orange-900 dark:bg-orange-950 dark:text-orange-200",
  MEDIUM: "border-amber-200 bg-amber-100 text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200",
  LOW: "border-green-200 bg-green-100 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200",
}

const statusStyles: Record<TestCaseStatus, string> = {
  READY: "border-green-200 bg-green-100 text-green-800 dark:border-green-900 dark:bg-green-950 dark:text-green-200",
  DRAFT: "border-slate-200 bg-slate-100 text-slate-700 dark:border-slate-800 dark:bg-slate-900 dark:text-slate-200",
  DEPRECATED: "border-red-200 bg-red-100 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200",
}

function label(value: string) {
  return value.charAt(0) + value.slice(1).toLowerCase()
}

export function PriorityBadge({ priority }: { priority: Priority }) {
  return (
    <Badge variant="outline" className={priorityStyles[priority]}>
      {label(priority)}
    </Badge>
  )
}

export function StatusBadge({ status }: { status: TestCaseStatus }) {
  return (
    <Badge variant="outline" className={statusStyles[status]}>
      {label(status)}
    </Badge>
  )
}