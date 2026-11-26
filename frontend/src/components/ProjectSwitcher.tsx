import NativeSelect from "@/components/NativeSelect"
import { useSelectedProject } from "@/hooks/useSelectedProject"

export default function ProjectSwitcher() {
  const { projects, projectId, selectProject } = useSelectedProject()
  if (projects.length === 0 || projectId === null) return null
  return (
    <NativeSelect
      className="w-44 sm:w-56"
      aria-label="Current project"
      value={String(projectId)}
      onChange={(event) => selectProject(event.target.value)}
    >
      {projects.map((project) => (
        <option key={project.id} value={String(project.id)}>
          {project.name}
        </option>
      ))}
    </NativeSelect>
  )
}
