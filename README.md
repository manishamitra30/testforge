![Tests](https://img.shields.io/badge/tests-274%20passing-brightgreen) ![Coverage](https://img.shields.io/badge/service%20%2B%20controller%20coverage-96%25%E2%80%9399%25-brightgreen)

# testforge

**Live demo:** https://testforge-gamma.vercel.app (free hosting, so the first load can take about a minute)

## Testing

274 automated backend tests, all passing:

- **Unit tests** (JUnit 5, Mockito): services, mappers, domain rules.
- **Integration tests** (Testcontainers + real PostgreSQL 15): Flyway schema, native/JPQL dashboard queries, `@EntityGraph` / N+1 guards, transactional workflows.
- **API tests** (MockMvc + Spring Security): JWT auth, validation errors, pagination/sorting, owner-only access control, end-to-end QA workflow.
- **Coverage** (JaCoCo): service 99%, controller 96%. Report: `backend/target/site/jacoco/index.html`.

Run them with Docker Desktop running:

```powershell
cd backend
.\mvnw.cmd test
```

The tests found a real bug: an invalid `?sort=` value returned HTTP 500 instead of 400 (now fixed). See [docs/TEST_PLAN.md](docs/TEST_PLAN.md).

## Frontend

React 19 + TypeScript + Vite, styled with Tailwind CSS and shadcn/ui.

- **Dashboard:** KPI cards, 30-day pass-rate trend, defects by severity, runs per week, flaky tests and recent activity (Recharts).
- **Projects, Suites and Test Cases:** searchable, filterable, paginated table with a dynamic steps editor.
- **Test Runs:** record Pass / Fail / Blocked per case, comment, and log a defect from a failed result in one click. Automated runs stream live output over WebSocket (STOMP).
- **Defects:** Kanban board with drag-and-drop that follows the backend's allowed status transitions, plus a table view.
- **Foundations:** TanStack Query for server state, React Hook Form + Zod for forms with server-side field errors, JWT in an Axios interceptor with automatic redirect on 401, protected routes, light/dark theme.

### Run it locally

```powershell
docker compose up -d                                                     # PostgreSQL
cd backend; powershell -ExecutionPolicy Bypass -File .\start-backend.ps1   # API on http://localhost:8080
cd frontend; npm install; npm run dev                                    # UI on http://localhost:5173
```

### Demo data

Register an account in the UI, then load two sample projects with a month of runs, flaky tests and defects so the charts are populated:

```powershell
Get-Content docs\demo-data.sql | docker exec -i testforge-db psql -U testforge -d testforge -v ON_ERROR_STOP=1 -v username=YOUR_USERNAME
```

## Screenshots

| Dashboard | Test run |
|---|---|
| ![Dashboard](docs/screenshots/dashboard.png) | ![Test run](docs/screenshots/test-run.png) |

| Defects board | Test cases |
|---|---|
| ![Defects board](docs/screenshots/defects-board.png) | ![Test cases](docs/screenshots/test-cases.png) |
<!-- section-9 -->
## Run with Docker

```bash
docker compose up -d                       # database only (for development)
docker compose --profile full up --build   # database + backend + frontend
```

Frontend http://localhost:3000, backend http://localhost:8080/actuator/health. Settings and the JWT secret come from the
environment or a `.env` file (see `.env.example`).

## CI/CD and deployment

[![CI](https://github.com/manishamitra30/testforge/actions/workflows/ci.yml/badge.svg)](https://github.com/manishamitra30/testforge/actions/workflows/ci.yml)

GitHub Actions runs the backend tests with a JaCoCo coverage gate, the frontend lint, type check and build, and a full
Docker stack start-up on every push and pull request. The app deploys to Neon (PostgreSQL), Render (backend) and Vercel
(frontend). See [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) for the steps and `scripts/smoke-test.ps1` to verify a deployment.