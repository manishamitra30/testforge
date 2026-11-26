import NativeSelect from "@/components/NativeSelect"
import type { Project } from "@/types"

interface Props {
  projects: Project[]
  projectId: number
  onChange: (id: string) => void
}

export default function ProjectPicker({ projects, projectId, onChange }: Props) {
  return (
    <NativeSelect
      className="w-52"
      aria-label="Project"
      value={String(projectId)}
      onChange={(event) => onChange(event.target.value)}
    >
      {projects.map((project) => (
        <option key={project.id} value={String(project.id)}>
          {project.name}
        </option>
      ))}
    </NativeSelect>
  )
}
