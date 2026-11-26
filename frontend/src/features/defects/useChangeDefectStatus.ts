import { useMutation, useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"
import { getErrorMessage } from "@/api/client"
import { changeDefectStatus } from "@/api/defects"
import type { PageResponse } from "@/types"
import type { Defect, DefectStatus } from "@/types/defect"

/** Moves a defect to a new status. The board updates instantly and rolls back if the server refuses. */
export function useChangeDefectStatus(queryKey: readonly unknown[]) {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: DefectStatus }) => changeDefectStatus(id, status),
    onMutate: async ({ id, status }) => {
      await queryClient.cancelQueries({ queryKey })
      const previous = queryClient.getQueryData<PageResponse<Defect>>(queryKey)
      if (previous) {
        queryClient.setQueryData<PageResponse<Defect>>(queryKey, {
          ...previous,
          content: previous.content.map((defect) => (defect.id === id ? { ...defect, status } : defect)),
        })
      }
      return { previous }
    },
    onError: (error, _variables, context) => {
      if (context?.previous) queryClient.setQueryData(queryKey, context.previous)
      toast.error(getErrorMessage(error))
    },
    onSettled: () => {
      queryClient.invalidateQueries({ queryKey: ["defects"] })
      queryClient.invalidateQueries({ queryKey: ["dash"] })
    },
  })
}
