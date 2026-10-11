import { useState, type ReactNode } from "react"
import { zodResolver } from "@hookform/resolvers/zod"
import { useForm } from "react-hook-form"
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom"
import { FlaskConical } from "lucide-react"
import { toast } from "sonner"
import { z } from "zod"
import { login, register as registerUser } from "@/api/auth"
import { getErrorMessage } from "@/api/client"
import { Button } from "@/components/ui/button"
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/context/AuthContext"

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
  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 p-4">
      <div className="w-full max-w-sm space-y-6">
        <div className="flex items-center justify-center gap-2 text-xl font-semibold">
          <FlaskConical className="size-6 text-primary" />
          TestForge
        </div>
        <Card>
          <CardHeader>
            <CardTitle>{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </CardHeader>
          <CardContent>{children}</CardContent>
        </Card>
        <p className="text-center text-sm text-muted-foreground">{footer}</p>
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
          <Link to="/register" className="font-medium text-primary hover:underline">
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
          <Link to="/login" className="font-medium text-primary hover:underline">
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