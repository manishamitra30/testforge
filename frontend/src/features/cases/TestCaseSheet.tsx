import { zodResolver } from "@hookform/resolvers/zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useFieldArray, useForm } from "react-hook-form"
import { Plus, Trash2 } from "lucide-react"
import { toast } from "sonner"
import { z } from "zod"
import { createCase } from "@/api/cases"
import { getErrorMessage } from "@/api/client"
import NativeSelect from "@/components/NativeSelect"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet"
import { Textarea } from "@/components/ui/textarea"
import type { Priority, TestCaseStatus, TestSuite } from "@/types"

const PRIORITIES: Priority[] = ["LOW", "MEDIUM", "HIGH", "CRITICAL"]
const STATUSES: TestCaseStatus[] = ["DRAFT", "READY", "DEPRECATED"]
const toLabel = (value: string) => value.charAt(0) + value.slice(1).toLowerCase()

const schema = z.object({
  suiteId: z.string().min(1, "Choose a suite"),
  title: z.string().min(1, "Title is required").max(255, "Max 255 characters"),
  description: z.string(),
  preconditions: z.string(),
  priority: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
  status: z.enum(["DRAFT", "READY", "DEPRECATED"]),
  tags: z.string().max(255, "Max 255 characters"),
  steps: z.array(
    z.object({
      action: z.string().min(1, "Action is required"),
      expectedResult: z.string().min(1, "Expected result is required"),
    }),
  ),
})
type FormValues = z.infer<typeof schema>

function TestCaseForm({
  suites,
  defaultSuiteId,
  onDone,
}: {
  suites: TestSuite[]
  defaultSuiteId: number | null
  onDone: () => void
}) {
  const queryClient = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      suiteId: defaultSuiteId ? String(defaultSuiteId) : "",
      title: "",
      description: "",
      preconditions: "",
      priority: "MEDIUM",
      status: "DRAFT",
      tags: "",
      steps: [{ action: "", expectedResult: "" }],
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: "steps" })

  const mutation = useMutation({
    mutationFn: (values: FormValues) =>
      createCase(Number(values.suiteId), {
        title: values.title.trim(),
        description: values.description.trim() || undefined,
        preconditions: values.preconditions.trim() || undefined,
        priority: values.priority,
        status: values.status,
        tags: values.tags.trim() || undefined,
        steps: values.steps.map((step, index) => ({
          stepNumber: index + 1,
          action: step.action.trim(),
          expectedResult: step.expectedResult.trim(),
        })),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["cases"] })
      toast.success("Test case created")
      onDone()
    },
    onError: (error) => toast.error(getErrorMessage(error)),
  })

  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      className="flex flex-col gap-4 px-4 pb-6"
    >
      <div className="space-y-2">
        <Label htmlFor="case-suite">Suite</Label>
        <NativeSelect id="case-suite" {...register("suiteId")}>
          <option value="">Select a suite</option>
          {suites.map((suite) => (
            <option key={suite.id} value={String(suite.id)}>
              {suite.name}
            </option>
          ))}
        </NativeSelect>
        {errors.suiteId && <p className="text-sm text-destructive">{errors.suiteId.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="case-title">Title</Label>
        <Input id="case-title" {...register("title")} />
        {errors.title && <p className="text-sm text-destructive">{errors.title.message}</p>}
      </div>

      <div className="grid grid-cols-2 gap-4">
        <div className="space-y-2">
          <Label htmlFor="case-priority">Priority</Label>
          <NativeSelect id="case-priority" {...register("priority")}>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {toLabel(p)}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div className="space-y-2">
          <Label htmlFor="case-status">Status</Label>
          <NativeSelect id="case-status" {...register("status")}>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {toLabel(s)}
              </option>
            ))}
          </NativeSelect>
        </div>
      </div>

      <div className="space-y-2">
        <Label htmlFor="case-tags">Tags</Label>
        <Input id="case-tags" placeholder="login, smoke" {...register("tags")} />
        {errors.tags && <p className="text-sm text-destructive">{errors.tags.message}</p>}
      </div>

      <div className="space-y-2">
        <Label htmlFor="case-description">Description</Label>
        <Textarea id="case-description" rows={3} {...register("description")} />
      </div>

      <div className="space-y-2">
        <Label htmlFor="case-preconditions">Preconditions</Label>
        <Textarea id="case-preconditions" rows={2} {...register("preconditions")} />
      </div>

      <div className="space-y-3">
        <div className="flex items-center justify-between">
          <Label>Steps</Label>
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => append({ action: "", expectedResult: "" })}
          >
            <Plus className="mr-1 size-4" />
            Add step
          </Button>
        </div>
        {fields.length === 0 && <p className="text-sm text-muted-foreground">No steps yet.</p>}
        {fields.map((field, index) => (
          <div key={field.id} className="space-y-2 rounded-md border p-3">
            <div className="flex items-center justify-between">
              <span className="text-sm font-medium">Step {index + 1}</span>
              <Button
                type="button"
                variant="ghost"
                size="icon"
                aria-label={`Remove step ${index + 1}`}
                onClick={() => remove(index)}
              >
                <Trash2 className="size-4" />
              </Button>
            </div>
            <Input placeholder="Action" {...register(`steps.${index}.action`)} />
            {errors.steps?.[index]?.action && (
              <p className="text-sm text-destructive">{errors.steps[index]?.action?.message}</p>
            )}
            <Input placeholder="Expected result" {...register(`steps.${index}.expectedResult`)} />
            {errors.steps?.[index]?.expectedResult && (
              <p className="text-sm text-destructive">{errors.steps[index]?.expectedResult?.message}</p>
            )}
          </div>
        ))}
      </div>

      <Button type="submit" disabled={mutation.isPending}>
        {mutation.isPending ? "Saving..." : "Create test case"}
      </Button>
    </form>
  )
}

export default function TestCaseSheet({
  open,
  onOpenChange,
  suites,
  defaultSuiteId,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  suites: TestSuite[]
  defaultSuiteId: number | null
}) {
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="right" className="w-full overflow-y-auto sm:max-w-xl">
        <SheetHeader>
          <SheetTitle>New test case</SheetTitle>
          <SheetDescription>Describe the case and add its steps.</SheetDescription>
        </SheetHeader>
        <TestCaseForm
          suites={suites}
          defaultSuiteId={defaultSuiteId ?? suites[0]?.id ?? null}
          onDone={() => onOpenChange(false)}
        />
      </SheetContent>
    </Sheet>
  )
}