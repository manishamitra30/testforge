import { NavLink, Outlet, useNavigate } from "react-router-dom"
import { useQueryClient } from "@tanstack/react-query"
import {
  Bug,
  FlaskConical,
  FolderKanban,
  Layers,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Play,
  Settings,
} from "lucide-react"
import ProjectSwitcher from "@/components/ProjectSwitcher"
import ThemeToggle from "@/components/ThemeToggle"
import { Button } from "@/components/ui/button"
import { useAuth } from "@/context/AuthContext"
import { useSelectedProject } from "@/hooks/useSelectedProject"
import { cn } from "@/lib/utils"

const navItems = [
  { to: "/dashboard", label: "Dashboard", icon: LayoutDashboard },
  { to: "/projects", label: "Projects", icon: FolderKanban },
  { to: "/suites", label: "Suites", icon: Layers },
  { to: "/test-cases", label: "Test Cases", icon: ListChecks },
  { to: "/test-runs", label: "Test Runs", icon: Play },
  { to: "/defects", label: "Defects", icon: Bug },
  { to: "/settings", label: "Settings", icon: Settings },
]

export default function AppShell() {
  const { user, signOut } = useAuth()
  const { projectId } = useSelectedProject()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  // Keep the chosen project when moving between pages.
  const search = projectId !== null ? `?project=${projectId}` : ""

  function handleLogout() {
    signOut()
    queryClient.clear()
    navigate("/login", { replace: true })
  }

  return (
    <div className="flex min-h-screen bg-muted/30">
      <aside className="hidden w-60 shrink-0 flex-col border-r bg-background md:flex">
        <div className="flex h-14 items-center gap-2 border-b px-4 text-lg font-semibold">
          <FlaskConical className="size-5 text-primary" />
          TestForge
        </div>
        <nav className="flex flex-1 flex-col gap-1 p-3" aria-label="Main">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={{ pathname: to, search }}
              className={({ isActive }) =>
                cn(
                  "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors",
                  isActive
                    ? "bg-primary text-primary-foreground"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-14 items-center gap-3 border-b bg-background px-4 sm:px-6">
          <div className="flex items-center gap-2 font-semibold md:hidden">
            <FlaskConical className="size-5 text-primary" />
            <span className="hidden sm:inline">TestForge</span>
          </div>
          <ProjectSwitcher />
          <div className="ml-auto flex items-center gap-2 sm:gap-3">
            <ThemeToggle />
            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium leading-tight">{user?.username}</p>
              <p className="text-xs text-muted-foreground">{user?.role}</p>
            </div>
            <div
              className="flex size-8 items-center justify-center rounded-full bg-primary text-sm font-medium text-primary-foreground"
              aria-hidden
            >
              {user?.username.charAt(0).toUpperCase()}
            </div>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              <LogOut className="mr-1 size-4" />
              <span className="hidden sm:inline">Logout</span>
            </Button>
          </div>
        </header>

        <nav className="flex gap-1 overflow-x-auto border-b bg-background px-3 py-2 md:hidden" aria-label="Main (compact)">
          {navItems.map(({ to, label, icon: Icon }) => (
            <NavLink
              key={to}
              to={{ pathname: to, search }}
              className={({ isActive }) =>
                cn(
                  "flex shrink-0 items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium",
                  isActive ? "bg-primary text-primary-foreground" : "text-muted-foreground hover:bg-muted",
                )
              }
            >
              <Icon className="size-4" />
              {label}
            </NavLink>
          ))}
        </nav>

        <main className="flex-1 p-4 sm:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
