import { useState } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useForm } from "react-hook-form"
import { Plus } from "lucide-react"
import { toast } from "sonner"
import { z } from "zod"
import { getErrorMessage } from "@/api/client"
import { createSuite } from "@/api/projects"
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
  name: z.string().min(1, "Name is required").max(150, "Max 150 characters"),
})
type FormValues = z.infer<typeof schema>

export default function NewSuiteDialog({ projectId }: { projectId: number }) {
  const [open, setOpen] = useState(false)
  const queryClient = useQueryClient()
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { name: "" } })

  const mutation = useMutation({
    mutationFn: (values: FormValues) => createSuite(projectId, { name: values.name.trim() }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["suites", projectId] })
      toast.success("Suite created")
      reset()
      setOpen(false)
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  })

  return (
    <>
      <Button variant="outline" onClick={() => setOpen(true)}>
        <Plus className="mr-1 size-4" />
        New Suite
      </Button>
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>New test suite</DialogTitle>
            <DialogDescription>Suites group related test cases inside a project.</DialogDescription>
          </DialogHeader>
          <form onSubmit={handleSubmit((values) => mutation.mutate(values))} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="suite-name">Name</Label>
              <Input id="suite-name" {...register("name")} />
              {errors.name && <p className="text-sm text-destructive">{errors.name.message}</p>}
            </div>
            <DialogFooter>
              <Button type="submit" disabled={mutation.isPending}>
                {mutation.isPending ? "Creating..." : "Create suite"}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </>
  )
}