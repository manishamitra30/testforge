import { api } from "./client"

export interface SampleProject {
  projectId: number
  created: boolean
}

/** Adds the sample project for the signed-in user. Pass a token right after registering, before it is stored. */
export const createSampleProject = (token?: string) =>
  api
    .post<SampleProject>(
      "/api/sample-project",
      null,
      token ? { headers: { Authorization: `Bearer ${token}` } } : undefined,
    )
    .then((r) => r.data)