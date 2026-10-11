import { api } from "./client"
import type {
  PageResponse,
  Project,
  ProjectRequest,
  TestSuite,
  TestSuiteRequest,
} from "@/types"

export const fetchProjects = () =>
  api
    .get<PageResponse<Project>>("/api/projects", { params: { page: 0, size: 100 } })
    .then((r) => r.data)

export const createProject = (data: ProjectRequest) =>
  api.post<Project>("/api/projects", data).then((r) => r.data)

export const fetchSuites = (projectId: number) =>
  api
    .get<PageResponse<TestSuite>>(`/api/projects/${projectId}/suites`, {
      params: { page: 0, size: 100 },
    })
    .then((r) => r.data)

export const createSuite = (projectId: number, data: TestSuiteRequest) =>
  api.post<TestSuite>(`/api/projects/${projectId}/suites`, data).then((r) => r.data)