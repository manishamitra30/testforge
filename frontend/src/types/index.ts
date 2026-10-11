export type Priority = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL"
export type TestCaseStatus = "DRAFT" | "READY" | "DEPRECATED"

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface AuthResponse {
  token: string
  tokenType: string
  username: string
  email: string
  role: string
}

export interface User {
  username: string
  email: string
  role: string
}

export interface Project {
  id: number
  name: string
  description: string | null
  ownerId: number
  createdAt: string
}

export interface ProjectRequest {
  name: string
  description?: string
}

export interface TestSuite {
  id: number
  projectId: number
  name: string
  description: string | null
}

export interface TestSuiteRequest {
  name: string
  description?: string
}

export interface TestStep {
  stepNumber: number
  action: string
  expectedResult: string
}

export interface TestCaseSummary {
  id: number
  suiteId: number
  title: string
  priority: Priority
  status: TestCaseStatus
  tags: string | null
  updatedAt: string
}

export interface TestCaseRequest {
  title: string
  description?: string
  preconditions?: string
  priority: Priority
  status: TestCaseStatus
  tags?: string
  steps: TestStep[]
}

export interface TestCase extends TestCaseSummary {
  description: string | null
  preconditions: string | null
  createdBy: number | null
  createdAt: string
  steps: TestStep[]
}