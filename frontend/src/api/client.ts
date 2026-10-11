import axios from "axios"

export const TOKEN_KEY = "testforge.token"
export const USER_KEY = "testforge.user"

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? "http://localhost:8080",
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const url: string = error.config?.url ?? ""
    if (status === 401 && !url.startsWith("/api/auth/")) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (window.location.pathname !== "/login") {
        window.location.assign("/login")
      }
    }
    return Promise.reject(error)
  },
)

export function getErrorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as
      | { message?: string; fieldErrors?: Record<string, string> }
      | undefined
    if (data?.fieldErrors && Object.keys(data.fieldErrors).length > 0) {
      return Object.entries(data.fieldErrors)
        .map(([field, message]) => `${field}: ${message}`)
        .join(", ")
    }
    if (data?.message) return data.message
    if (!error.response) return "Cannot reach the server. Is the backend running?"
  }
  return "Something went wrong"
}