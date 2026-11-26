import { useQuery } from "@tanstack/react-query"
import { useSearchParams } from "react-router-dom"
import { fetchProjects } from "@/api/projects"

const STORAGE_KEY = "testforge.project"

function storedProjectId(): number {
  try {
    return Number(localStorage.getItem(STORAGE_KEY))
  } catch {
    return 0
  }
}

/**
 * The project chosen in the top bar. It lives in the URL (?project=ID) so links and refreshes keep it,
 * and is remembered in the browser so pages opened without the parameter use the last choice.
 */
export function useSelectedProject() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = useQuery({ queryKey: ["projects"], queryFn: fetchProjects })
  const projects = query.data?.content ?? []

  const known = (id: number) => projects.some((project) => project.id === id)
  const paramId = Number(searchParams.get("project"))
  const savedId = storedProjectId()
  const projectId = known(paramId) ? paramId : known(savedId) ? savedId : (projects[0]?.id ?? null)

  function selectProject(id: number | string) {
    try {
      localStorage.setItem(STORAGE_KEY, String(id))
    } catch {
      // the choice just won't be remembered
    }
    setSearchParams((previous) => {
      const next = new URLSearchParams(previous)
      next.set("project", String(id))
      return next
    })
  }

  return { projects, projectId, selectProject, isLoading: query.isLoading, isError: query.isError }
}
