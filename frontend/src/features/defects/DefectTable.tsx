import { Link } from "react-router-dom"
import { DefectStatusBadge, SeverityBadge, defectStatusLabel } from "@/components/DefectBadges"
import NativeSelect from "@/components/NativeSelect"
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table"
import type { Defect, DefectStatus } from "@/types/defect"

interface Props {
  defects: Defect[]
  onMove: (id: number, status: DefectStatus) => void
}

export default function DefectTable({ defects, onMove }: Props) {
  return (
    <div className="rounded-xl border bg-background">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Defect</TableHead>
            <TableHead>Severity</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Test case</TableHead>
            <TableHead>Run</TableHead>
            <TableHead>Assignee</TableHead>
            <TableHead>Created</TableHead>
            <TableHead className="w-40">Move to</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {defects.map((defect) => (
            <TableRow key={defect.id}>
              <TableCell className="max-w-72 truncate font-medium" title={defect.title}>
                {defect.title}
              </TableCell>
              <TableCell>
                <SeverityBadge severity={defect.severity} />
              </TableCell>
              <TableCell>
                <DefectStatusBadge status={defect.status} />
              </TableCell>
              <TableCell className="max-w-48 truncate text-muted-foreground">{defect.testCaseTitle ?? "-"}</TableCell>
              <TableCell>
                {defect.runId ? (
                  <Link to={`/runs/${defect.runId}`} className="text-sm hover:underline">
                    #{defect.runId}
                  </Link>
                ) : (
                  "-"
                )}
              </TableCell>
              <TableCell className="text-muted-foreground">{defect.assigneeName ?? "Unassigned"}</TableCell>
              <TableCell className="text-sm text-muted-foreground">
                {new Date(defect.createdAt).toLocaleDateString(undefined, { day: "numeric", month: "short" })}
              </TableCell>
              <TableCell>
                {defect.allowedTransitions.length === 0 ? (
                  <span className="text-xs text-muted-foreground">-</span>
                ) : (
                  <NativeSelect
                    aria-label={`Move "${defect.title}" to`}
                    value=""
                    onChange={(event) => event.target.value && onMove(defect.id, event.target.value as DefectStatus)}
                  >
                    <option value="">Move to...</option>
                    {defect.allowedTransitions.map((status) => (
                      <option key={status} value={status}>
                        {defectStatusLabel[status]}
                      </option>
                    ))}
                  </NativeSelect>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  )
}
