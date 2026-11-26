import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { useNavigate } from "react-router-dom"
import { Play } from "lucide-react"
import { toast } from "sonner"
import { z } from "zod"
import { getErrorMessage } from "@/api/client"
import { fetchSuites } from "@/api/projects"
import { createRun } from "@/api/runs"
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

const schema = z.object({
  suiteId: z.string().min(1, "Choose a suite"),
  name: z.string().trim().min(1, "Name is required").max(150, "Max 150 characters"),
})
type FormValues = z.infer<typeof schema>

export default function NewRunDialog({ projectId }: { projectId: number }) {
  const [open, setOpen] = useState(false)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const suitesQuery = useQuery({
    queryKey: ["suites", projectId],
    queryFn: () => fetchSuites(projectId),
    enabled: open,
  })
  const suites = suitesQuery.data?.content ?? []

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { suiteId: "", name: "" } })

  const mutation = useMutation({
    mutationFn: (values: FormValues) => createRun(Number(values.suiteId), values.name.trim()),
    onSuccess: (run) => {
      queryClient.invalidateQueries({ queryKey: ["runs"] })
      queryClient.invalidateQueries({ queryKey: ["dash"] })
      toast.success("Run started")
      reset()
      setOpen(false)
      navigate(`/runs/${run.id}`)
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  })

  return (
    <>
      <Button onClick={() => setOpen(true)}>
        <Play className="mr-1 size-4" />
        New Run
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Start a manual run</DialogTitle>
            <DialogDescription>
              Every active test case in the suite is added to the run as "Not run" so you can record results.
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="run-suite">Suite</Label>
              <NativeSelect id="run-suite" {...register("suiteId")}>
                <option value="">{suitesQuery.isLoading ? "Loading..." : "Select a suite"}</option>
                {suites.map((suite) => (
                  <option key={suite.id} value={String(suite.id)}>
                    {suite.name}
                  </option>
                ))}
              </NativeSelect>
              {errors.suiteId && <p className="text-sm text-destructive">{errors.suiteId.message}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="run-name">Run name</Label>
              <Input id="run-name" placeholder="e.g. Sprint 12 regression" {...register("name")} />
              {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
            </div>
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? "Starting..." : "Start run"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}
