import { useTheme } from "next-themes"
import { useNavigate } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"
import { LogOut, Monitor, Moon, Sun } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { useAuth } from "@/context/AuthContext"

const themes = [
  { value: "light", label: "Light", icon: Sun },
  { value: "dark", label: "Dark", icon: Moon },
  { value: "system", label: "System", icon: Monitor },
] as const

export default function SettingsPage() {
  const { user, signOut } = useAuth()
  const { theme, setTheme } = useTheme()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const apiUrl = (import.meta.env.VITE_API_URL as string | undefined) ?? "http://localhost:8080"

  function handleLogout() {
    signOut()
    queryClient.clear()
    navigate("/login", { replace: true })
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Settings</h1>
        <p className="text-sm text-muted-foreground">Your account and how TestForge looks for you.</p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Profile</CardTitle>
          <CardDescription>The account you are signed in with.</CardDescription>
        </CardHeader>
        <CardContent className="flex items-center gap-4">
          <div className="flex size-14 items-center justify-center rounded-full bg-primary text-xl font-semibold text-primary-foreground">
            {user?.username.charAt(0).toUpperCase()}
          </div>
          <dl className="grid gap-1 text-sm">
            <div className="flex items-center gap-2">
              <dt className="w-20 text-muted-foreground">Username</dt>
              <dd className="font-medium">{user?.username}</dd>
            </div>
            <div className="flex items-center gap-2">
              <dt className="w-20 text-muted-foreground">Email</dt>
              <dd className="font-medium">{user?.email}</dd>
            </div>
            <div className="flex items-center gap-2">
              <dt className="w-20 text-muted-foreground">Role</dt>
              <dd>
                <Badge variant="secondary">{user?.role}</Badge>
              </dd>
            </div>
          </dl>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Appearance</CardTitle>
          <CardDescription>Choose a theme. "System" follows your device setting.</CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label="Theme">
            {themes.map(({ value, label, icon: Icon }) => (
              <Button
                key={value}
                variant={theme === value ? "default" : "outline"}
                role="radio"
                aria-checked={theme === value}
                onClick={() => setTheme(value)}
              >
                <Icon className="mr-1 size-4" />
                {label}
              </Button>
            ))}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Session</CardTitle>
          <CardDescription>Connected to {apiUrl}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button variant="outline" onClick={handleLogout}>
            <LogOut className="mr-1 size-4" />
            Sign out
          </Button>
        </CardContent>
      </Card>
    </div>
  )
}
