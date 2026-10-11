import { Navigate, Route, Routes } from "react-router-dom"
import AppShell from "@/components/AppShell"
import { LoginPage, RegisterPage } from "@/pages/AuthPages"
import PlaceholderPage from "@/pages/PlaceholderPage"
import ProjectsPage from "@/pages/ProjectsPage"
import TestCasesPage from "@/pages/TestCasesPage"
import ProtectedRoute from "@/routes/ProtectedRoute"

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppShell />}>
          <Route index element={<Navigate to="/projects" replace />} />
          <Route path="/dashboard" element={<PlaceholderPage title="Dashboard" />} />
          <Route path="/projects" element={<ProjectsPage />} />
          <Route path="/test-cases" element={<TestCasesPage />} />
          <Route path="/test-runs" element={<PlaceholderPage title="Test Runs" />} />
          <Route path="/defects" element={<PlaceholderPage title="Defects" />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}