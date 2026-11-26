import { useQuery } from "@tanstack/react-query"
import { useSearchParams } from "react-router-dom"
import { fetchProjects } from "@/api/projects"

/** The project chosen in the page's selector, kept in the URL (?project=ID) so links and refreshes keep it. */
export function useSelectedProject() {
  const [searchParams, setSearchParams] = useSearchParams()
  const query = useQuery({ queryKey: ["projects"], queryFn: fetchProjects })
  const projects = query.data?.content ?? []
  const paramId = Number(searchParams.get("project"))
  const projectId = projects.some((p) => p.id === paramId) ? paramId : (projects[0]?.id ?? null)

  function selectProject(id: number | string) {
    setSearchParams({ project: String(id) })
  }

  return { projects, projectId, selectProject, isLoading: query.isLoading, isError: query.isError }
}
