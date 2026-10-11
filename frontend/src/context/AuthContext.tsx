import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react"
import { TOKEN_KEY, USER_KEY } from "@/api/client"
import type { AuthResponse, User } from "@/types"

interface AuthContextValue {
  user: User | null
  isAuthenticated: boolean
  signIn: (response: AuthResponse) => void
  signOut: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

function readStoredUser(): User | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    const token = localStorage.getItem(TOKEN_KEY)
    return raw && token ? (JSON.parse(raw) as User) : null
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(readStoredUser)

  const signIn = useCallback((response: AuthResponse) => {
    const nextUser: User = {
      username: response.username,
      email: response.email,
      role: response.role,
    }
    localStorage.setItem(TOKEN_KEY, response.token)
    localStorage.setItem(USER_KEY, JSON.stringify(nextUser))
    setUser(nextUser)
  }, [])

  const signOut = useCallback(() => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, isAuthenticated: user !== null, signIn, signOut }),
    [user, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth must be used inside AuthProvider")
  }
  return context
}