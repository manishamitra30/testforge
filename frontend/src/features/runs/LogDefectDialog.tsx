import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { toast } from "sonner"
import { z } from "zod"
import { createDefect } from "@/api/defects"
import { getErrorMessage } from "@/api/client"
import NativeSelect from "@/components/NativeSelect"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Textarea } from "@/components/ui/textarea"
import type { Severity } from "@/types/dashboard"
import type { RunResult } from "@/types/run"

const schema = z.object({
  title: z.string().max(255, "Max 255 characters"),
  severity: z.string(),
  description: z.string(),
})
type FormValues = z.infer<typeof schema>

interface Props {
  result: RunResult | null
  onClose: () => void
}

export default function LogDefectDialog({ result, onClose }: Props) {
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { title: "", severity: "", description: "" },
  })

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      createDefect({
        testResultId: (result as RunResult).id,
        title: values.title.trim() || undefined,
        description: values.description.trim() || undefined,
        severity: (values.severity || undefined) as Severity | undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["defects"] })
      queryClient.invalidateQueries({ queryKey: ["dash"] })
      toast.success("Defect logged")
      reset()
      onClose()
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  })

  return (
    <Dialog open={result !== null} onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Log defect</DialogTitle>
          <DialogDescription>
            For "{result?.title}". Leave the fields blank to use sensible defaults from the failed result.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="defect-title">Title (optional)</Label>
            <Input id="defect-title" {...register("title")} />
            {errors.title && <p className="text-sm text-destructive">{errors.title.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="defect-severity">Severity</Label>
            <NativeSelect id="defect-severity" {...register("severity")}>
              <option value="">Automatic (from test priority)</option>
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
              <option value="CRITICAL">Critical</option>
            </NativeSelect>
          </div>
          <div className="space-y-2">
            <Label htmlFor="defect-description">Description (optional)</Label>
            <Textarea id="defect-description" rows={4} {...register("description")} />
          </div>
          <DialogFooter>
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Logging..." : "Log defect"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
