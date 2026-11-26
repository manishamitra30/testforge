import { useState } from "react"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { Bug, RotateCcw } from "lucide-react"
import { toast } from "sonner"
import { getErrorMessage } from "@/api/client"
import { updateResult } from "@/api/runs"
import { PriorityBadge } from "@/components/badges"
import { ResultBadge } from "@/components/RunBadges"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { TableCell, TableRow } from "@/components/ui/table"
import { cn } from "@/lib/utils"
import type { ResultStatus, RunResult } from "@/types/run"

const actions: { status: ResultStatus; label: string; active: string }[] = [
  { status: "PASSED", label: "Pass", active: "bg-green-600 text-white hover:bg-green-600/90" },
  { status: "FAILED", label: "Fail", active: "bg-red-600 text-white hover:bg-red-600/90" },
  { status: "BLOCKED", label: "Blocked", active: "bg-amber-500 text-white hover:bg-amber-500/90" },
]

interface Props {
  runId: number
  result: RunResult
  editable: boolean
  onLogDefect: (result: RunResult) => void
}

export default function ResultRow({ runId, result, editable, onLogDefect }: Props) {
  const queryClient = useQueryClient()
  const [comment, setComment] = useState(result.comment ?? "")

  const mutation = useMutation({
    mutationFn: (status: ResultStatus) => updateResult(runId, result.id, { status, comment }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["run", runId] })
      queryClient.invalidateQueries({ queryKey: ["runs"] })
      queryClient.invalidateQueries({ queryKey: ["dash"] })
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  })

  // The backend replaces the comment on every update, so saving a comment re-sends the current status.
  function saveComment() {
    if (editable && result.status !== "NOT_RUN" && comment !== (result.comment ?? "")) {
      mutation.mutate(result.status)
    }
  }

  return (
    <TableRow>
      <TableCell className="font-medium">{result.title}</TableCell>
      <TableCell>
        <PriorityBadge priority={result.priority} />
      </TableCell>
      <TableCell>
        <ResultBadge status={result.status} />
      </TableCell>
      {editable ? (
        <>
          <TableCell>
            <div className="flex flex-wrap items-center gap-1">
              {actions.map(({ status, label, active }) => (
                <Button
                  key={status}
                  size="sm"
                  variant="outline"
                  disabled={mutation.isPending}
                  className={cn(result.status === status && active)}
                  onClick={() => mutation.mutate(status)}
                >
                  {label}
                </Button>
              ))}
              {result.status !== "NOT_RUN" && (
                <Button
                  size="icon-sm"
                  variant="ghost"
                  title="Reset to not run"
                  aria-label="Reset to not run"
                  disabled={mutation.isPending}
                  onClick={() => mutation.mutate("NOT_RUN")}
                >
                  <RotateCcw className="size-4" />
                </Button>
              )}
            </div>
          </TableCell>
          <TableCell>
            <Input
              className="h-8 min-w-40"
              placeholder="Comment"
              value={comment}
              maxLength={5000}
              onChange={(event) => setComment(event.target.value)}
              onBlur={saveComment}
              onKeyDown={(event) => event.key === "Enter" && event.currentTarget.blur()}
            />
          </TableCell>
        </>
      ) : (
        <>
          <TableCell className="text-muted-foreground">
            {result.durationMs ? `${(result.durationMs / 1000).toFixed(1)}s` : "-"}
          </TableCell>
          <TableCell className="max-w-md truncate text-muted-foreground" title={result.errorMessage ?? ""}>
            {result.errorMessage ?? result.comment ?? "-"}
          </TableCell>
        </>
      )}
      <TableCell className="text-right">
        {result.status === "FAILED" && (
          <Button size="sm" variant="outline" onClick={() => onLogDefect(result)}>
            <Bug className="mr-1 size-4" />
            Log defect
          </Button>
        )}
      </TableCell>
    </TableRow>
  )
}
