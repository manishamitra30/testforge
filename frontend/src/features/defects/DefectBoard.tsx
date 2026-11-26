import { useState } from "react"
import {
  DndContext,
  DragOverlay,
  KeyboardSensor,
  PointerSensor,
  useDraggable,
  useDroppable,
  useSensor,
  useSensors,
  type DragEndEvent,
  type DragStartEvent,
} from "@dnd-kit/core"
import { toast } from "sonner"
import { Link } from "react-router-dom"
import { GripVertical } from "lucide-react"
import { SeverityBadge, defectStatusLabel } from "@/components/DefectBadges"
import { cn } from "@/lib/utils"
import type { Defect, DefectStatus } from "@/types/defect"

const COLUMNS: { status: DefectStatus; accent: string }[] = [
  { status: "OPEN", accent: "border-t-red-500" },
  { status: "IN_PROGRESS", accent: "border-t-blue-500" },
  { status: "RESOLVED", accent: "border-t-green-500" },
  { status: "CLOSED", accent: "border-t-slate-400" },
]

function CardBody({ defect, dragging }: { defect: Defect; dragging?: boolean }) {
  return (
    <div
      className={cn(
        "rounded-lg border bg-background p-3 text-sm shadow-xs",
        dragging && "rotate-1 shadow-lg ring-2 ring-primary/40",
      )}
    >
      <div className="flex items-start gap-2">
        <GripVertical className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
        <p className="min-w-0 flex-1 font-medium leading-snug">{defect.title}</p>
      </div>
      <div className="mt-2 flex flex-wrap items-center gap-2">
        <SeverityBadge severity={defect.severity} />
        {defect.assigneeName && <span className="text-xs text-muted-foreground">@{defect.assigneeName}</span>}
      </div>
      {(defect.testCaseTitle || defect.runId) && (
        <p className="mt-2 truncate text-xs text-muted-foreground" title={defect.testCaseTitle ?? ""}>
          {defect.testCaseTitle}
          {defect.runId && (
            <>
              {" Â· "}
              <Link
                to={`/runs/${defect.runId}`}
                className="underline-offset-2 hover:underline"
                onPointerDown={(event) => event.stopPropagation()}
              >
                Run #{defect.runId}
              </Link>
            </>
          )}
        </p>
      )}
    </div>
  )
}

function DraggableCard({ defect }: { defect: Defect }) {
  const { attributes, listeners, setNodeRef, transform, isDragging } = useDraggable({ id: defect.id })
  return (
    <div
      ref={setNodeRef}
      {...listeners}
      {...attributes}
      style={transform ? { transform: `translate3d(${transform.x}px, ${transform.y}px, 0)` } : undefined}
      className={cn("cursor-grab touch-none active:cursor-grabbing", isDragging && "opacity-40")}
    >
      <CardBody defect={defect} />
    </div>
  )
}

function Column({
  status,
  accent,
  defects,
  dragging,
}: {
  status: DefectStatus
  accent: string
  defects: Defect[]
  dragging: Defect | null
}) {
  const { setNodeRef, isOver } = useDroppable({ id: status })
  const allowed = dragging ? dragging.status === status || dragging.allowedTransitions.includes(status) : true
  const canDrop = dragging !== null && dragging.status !== status && allowed

  return (
    <div
      ref={setNodeRef}
      className={cn(
        "flex min-h-64 flex-col rounded-xl border border-t-4 bg-muted/40 p-3 transition-colors",
        accent,
        canDrop && "border-dashed bg-primary/5",
        canDrop && isOver && "bg-primary/10 ring-2 ring-primary/40",
        dragging && !allowed && "opacity-50",
      )}
    >
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold">{defectStatusLabel[status]}</h2>
        <span className="rounded-full bg-background px-2 py-0.5 text-xs tabular-nums text-muted-foreground">
          {defects.length}
        </span>
      </div>
      <div className="flex flex-1 flex-col gap-2">
        {defects.map((defect) => (
          <DraggableCard key={defect.id} defect={defect} />
        ))}
        {defects.length === 0 && (
          <p className="py-6 text-center text-xs text-muted-foreground">
            {canDrop ? "Drop here" : "No defects"}
          </p>
        )}
      </div>
    </div>
  )
}

interface Props {
  defects: Defect[]
  onMove: (id: number, status: DefectStatus) => void
}

export default function DefectBoard({ defects, onMove }: Props) {
  const [activeId, setActiveId] = useState<number | null>(null)
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor),
  )
  const active = defects.find((defect) => defect.id === activeId) ?? null

  function handleStart(event: DragStartEvent) {
    setActiveId(Number(event.active.id))
  }

  function handleEnd(event: DragEndEvent) {
    setActiveId(null)
    const defect = defects.find((d) => d.id === Number(event.active.id))
    const target = event.over?.id as DefectStatus | undefined
    if (!defect || !target || target === defect.status) return
    if (!defect.allowedTransitions.includes(target)) {
      toast.error(
        `A ${defectStatusLabel[defect.status].toLowerCase()} defect cannot move to ${defectStatusLabel[target].toLowerCase()}. ` +
          `Allowed: ${defect.allowedTransitions.map((s) => defectStatusLabel[s]).join(", ") || "none"}.`,
      )
      return
    }
    onMove(defect.id, target)
  }

  return (
    <DndContext sensors={sensors} onDragStart={handleStart} onDragEnd={handleEnd} onDragCancel={() => setActiveId(null)}>
      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        {COLUMNS.map(({ status, accent }) => (
          <Column
            key={status}
            status={status}
            accent={accent}
            defects={defects.filter((defect) => defect.status === status)}
            dragging={active}
          />
        ))}
      </div>
      <DragOverlay>{active ? <CardBody defect={active} dragging /> : null}</DragOverlay>
    </DndContext>
  )
}
