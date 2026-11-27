import { useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom"
import { Activity, Bug, FlaskConical, Zap } from "lucide-react"
import { toast } from "sonner"
import { z } from "zod"
import { login, register as registerUser } from "@/api/auth"
import { getErrorMessage } from "@/api/client"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/context/AuthContext"
import { useSpotlight } from "@/hooks/useSpotlight"

const AUTH_FEATURES = [
  { icon: Zap, text: "Live Selenium runs streamed over WebSocket" },
  { icon: Bug, text: "Defect workflow with enforced state transitions" },
  { icon: Activity, text: "Pass-rate trends and flaky-test analytics" },
]

function AuthLayout({
  title,
  description,
  footer,
  children,
}: {
  title: string
  description: string
  footer: ReactNode
  children: ReactNode
}) {
  useSpotlight()
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.1fr_1fr]">
      <aside className="relative hidden overflow-hidden bg-sidebar p-12 text-sidebar-foreground lg:flex lg:flex-col lg:justify-between">
        <div className="aurora aurora-1" />
        <div className="aurora aurora-2" />
        <div className="relative z-10 flex items-center gap-2 font-mono text-xl font-semibold">
          <FlaskConical className="size-6 text-sidebar-primary" />
          TestForge<span className="text-sidebar-primary">_</span>
        </div>
        <div className="relative z-10 space-y-8">
          <div className="space-y-4">
            <h2 className="gradient-text text-4xl font-semibold leading-tight">
              Design tests.
              <br />
              Run them live.
              <br />
              Ship with confidence.
            </h2>
            <p className="max-w-md text-sidebar-foreground/70">
              One workspace for test cases, automated runs, defects and quality metrics.
            </p>
          </div>
          <ul className="space-y-3">
            {AUTH_FEATURES.map(({ icon: Icon, text }) => (
              <li key={text} className="flex items-center gap-3 text-sm text-sidebar-foreground/90">
                <span className="flex size-8 items-center justify-center rounded-lg bg-sidebar-primary/15 text-sidebar-primary">
                  <Icon className="size-4" />
                </span>
                {text}
              </li>
            ))}
          </ul>
          <div className="max-w-md rounded-xl border border-sidebar-border bg-black/30 p-4 font-mono text-xs leading-6 backdrop-blur">
            <div className="terminal-line text-sidebar-foreground/60" style={{ "--i": 0 } as React.CSSProperties}>
              $ ./mvnw test
            </div>
            <div className="terminal-line text-emerald-400" style={{ "--i": 1 } as React.CSSProperties}>
              ✓ Login with valid credentials ........ PASSED
            </div>
            <div className="terminal-line text-emerald-400" style={{ "--i": 2 } as React.CSSProperties}>
              ✓ Create test suite ................... PASSED
            </div>
            <div className="terminal-line text-red-400" style={{ "--i": 3 } as React.CSSProperties}>
              ✗ Checkout with expired card .......... FAILED
            </div>
            <div className="terminal-line text-sidebar-primary" style={{ "--i": 4 } as React.CSSProperties}>
              → defect logged <span className="terminal-cursor" />
            </div>
          </div>
        </div>
        <p className="relative z-10 font-mono text-xs text-sidebar-foreground/50">
          Spring Boot · React · PostgreSQL · Selenium
        </p>
      </aside>

      <div className="flex items-center justify-center p-6">
        <div className="stagger w-full max-w-sm space-y-6">
          <div className="flex items-center justify-center gap-2 font-mono text-xl font-semibold lg:hidden">
            <FlaskConical className="size-6 text-primary" />
            TestForge<span className="text-primary">_</span>
          </div>
          <Card>
            <CardHeader>
              <CardTitle className="text-xl">{title}</CardTitle>
              <CardDescription>{description}</CardDescription>
            </CardHeader>
            <CardContent>{children}</CardContent>
          </Card>
          <p className="text-center text-sm text-muted-foreground">{footer}</p>
        </div>
      </div>
    </div>
  )
}

function FieldError({ message }: { message?: string }) {
  return message ? <p className="text-sm text-destructive">{message}</p> : null
}

const loginSchema = z.object({
  username: z.string().min(1, "Username is required"),
  password: z.string().min(1, "Password is required"),
})
type LoginValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const { isAuthenticated, signIn } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from =
    (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? "/projects"
  const [submitting, setSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  })

  if (isAuthenticated) {
    return <Navigate to={from} replace />
  }

  async function onSubmit(values: LoginValues) {
    setSubmitting(true)
    try {
      signIn(await login(values))
      navigate(from, { replace: true })
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Sign in"
      description="Manage your test cases, runs and defects."
      footer={
        <>
          No account?{" "}
          <Link to="/register" className="font-medium text-foreground underline decoration-primary decoration-2 underline-offset-4 hover:text-primary">
            Register
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <Input id="username" autoComplete="username" {...register("username")} />
          <FieldError message={errors.username?.message} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" autoComplete="current-password" {...register("password")} />
          <FieldError message={errors.password?.message} />
        </div>
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? "Signing in..." : "Sign in"}
        </Button>
      </form>
    </AuthLayout>
  )
}

const registerSchema = z.object({
  username: z.string().min(3, "At least 3 characters"),
  email: z.string().email("Enter a valid email"),
  password: z.string().min(8, "At least 8 characters"),
})
type RegisterValues = z.infer<typeof registerSchema>

export function RegisterPage() {
  const { isAuthenticated, signIn } = useAuth()
  const navigate = useNavigate()
  const [submitting, setSubmitting] = useState(false)

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { username: "", email: "", password: "" },
  })

  if (isAuthenticated) {
    return <Navigate to="/projects" replace />
  }

  async function onSubmit(values: RegisterValues) {
    setSubmitting(true)
    try {
      await registerUser(values)
      signIn(await login({ username: values.username, password: values.password }))
      toast.success("Account created")
      navigate("/projects", { replace: true })
    } catch (error) {
      toast.error(getErrorMessage(error))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthLayout
      title="Create an account"
      description="Start managing your tests in minutes."
      footer={
        <>
          Already registered?{" "}
          <Link to="/login" className="font-medium text-foreground underline decoration-primary decoration-2 underline-offset-4 hover:text-primary">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
        <div className="space-y-2">
          <Label htmlFor="username">Username</Label>
          <Input id="username" autoComplete="username" {...register("username")} />
          <FieldError message={errors.username?.message} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <Input id="email" type="email" autoComplete="email" {...register("email")} />
          <FieldError message={errors.email?.message} />
        </div>
        <div className="space-y-2">
          <Label htmlFor="password">Password</Label>
          <Input id="password" type="password" autoComplete="new-password" {...register("password")} />
          <FieldError message={errors.password?.message} />
        </div>
        <Button type="submit" className="w-full" disabled={submitting}>
          {submitting ? "Creating account..." : "Register"}
        </Button>
      </form>
    </AuthLayout>
  )
}