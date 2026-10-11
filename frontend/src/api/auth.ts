import { api } from "./client"
import type { AuthResponse } from "@/types"

export const login = (data: { username: string; password: string }) =>
  api.post<AuthResponse>("/api/auth/login", data).then((r) => r.data)

export const register = (data: { username: string; email: string; password: string }) =>
  api.post<{ message: string }>("/api/auth/register", data).then((r) => r.data)