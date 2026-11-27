# Docker, CI/CD and deployment

TestForge ships as three containers (PostgreSQL, Spring Boot backend, React frontend behind nginx), is checked by
GitHub Actions on every push, and runs in the cloud on three free-tier services:

```
Browser ──▶ Vercel (React build) ──HTTPS + WSS──▶ Render (Spring Boot, Docker) ──JDBC/SSL──▶ Neon (PostgreSQL)
```

## 1. Run everything in Docker locally

```powershell
# only the database (what you use while developing the backend/frontend from your IDE)
docker compose up -d

# the whole stack: database + backend + frontend
docker compose --profile full up --build
```

| Service  | URL                                   |
|----------|---------------------------------------|
| Frontend | http://localhost:3000                 |
| Backend  | http://localhost:8080/actuator/health |
| Database | localhost:5433 (user/db `testforge`)  |

Settings come from the environment or a `.env` file; copy `.env.example` to `.env` to change them. The defaults are for
local use only (the bundled JWT key is public).

* `WITH_AUTOMATION=true` (default) puts Chromium and chromedriver in the backend image so **automated runs** work in the
  container. The image is large (about 1 GB). Use `WITH_AUTOMATION=false` for a small image without a browser.
* The database data lives in the named volume `pgdata`, so `docker compose down` keeps it and `down -v` wipes it.
* `VITE_API_URL` / `VITE_WS_URL` are compiled into the frontend bundle, so they are build arguments, not runtime settings.

## 2. Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request to `main`:

| Job      | What it does                                                                                              |
|----------|-----------------------------------------------------------------------------------------------------------|
| backend  | `mvnw verify` (unit + Testcontainers integration + API tests), JaCoCo report, fails below 60 % coverage     |
| frontend | `npm ci`, `npm run lint`, `npm run build` (which runs `tsc -b`, so it is the type check)                    |
| docker   | starts the whole compose stack, waits for health checks, smoke-tests it, then builds the full backend image |

Reports (JaCoCo, Surefire) are attached to each run as the `backend-reports` artifact. Raise `MIN_COVERAGE` in the
workflow as coverage grows.

## 3. Deploy

### 3.1 Database: Neon

1. Create a project at neon.tech (any region near your backend).
2. Copy the **direct** connection string (host *without* `-pooler`). Flyway takes a database lock during migration and
   that does not work reliably through a transaction-mode pooler.
3. Turn it into the three backend settings:

   ```
   postgresql://USER:PASSWORD@ep-xxxx.region.aws.neon.tech/neondb?sslmode=require
   →  DB_URL      = jdbc:postgresql://ep-xxxx.region.aws.neon.tech/neondb?sslmode=require
      DB_USERNAME = USER
      DB_PASSWORD = PASSWORD
   ```

Flyway creates the schema (V1–V4) the first time the backend starts; there is nothing to run by hand.

### 3.2 Backend: Render

The repo contains a Blueprint (`render.yaml`).

1. Render dashboard → **New → Blueprint** → pick the `testforge` repository.
2. Fill in the prompted values: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and, for now, `CORS_ALLOWED_ORIGINS=http://localhost:5173`
   (you set the real value in step 3.4). `JWT_SECRET` is generated for you.
3. Wait for the build, then open `https://<your-service>.onrender.com/actuator/health`. It must show `{"status":"UP"}`.

Doing it by hand instead: **New → Web Service → Docker**, Dockerfile path `backend/Dockerfile`, Docker context `.`,
health check path `/actuator/health`, and the same environment variables plus `SPRING_PROFILES_ACTIVE=prod` and
`WITH_AUTOMATION=false`.

The `prod` profile has no defaults for secrets, so the service refuses to start when one is missing. Swagger UI and the
API docs are switched off in this profile on purpose.

| Variable               | Meaning                                                                               |
|------------------------|---------------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE` | `prod`                                                                              |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Neon connection (section 3.1)                                         |
| `JWT_SECRET`           | Base64 key, at least 32 bytes decoded (`openssl rand -base64 32`)                      |
| `CORS_ALLOWED_ORIGINS` | Exact frontend origin(s), comma separated, **no trailing slash**. Used for REST and WebSocket |
| `WITH_AUTOMATION`      | Build arg: `false` = small image without Chromium                                     |
| `PORT`                 | Set by the platform; the app listens on it                                            |

### 3.3 Frontend: Vercel

1. Vercel → **Add New → Project** → import the repository.
2. **Root Directory:** `frontend`. Framework preset: Vite (build `npm run build`, output `dist`).
3. Environment variables (these are baked in at build time):
   * `VITE_API_URL` = `https://<your-service>.onrender.com`
   * `VITE_WS_URL` = `wss://<your-service>.onrender.com/ws` (optional; derived from `VITE_API_URL` when empty)
4. Deploy. `frontend/vercel.json` rewrites every path to `index.html`, so refreshing `/dashboard` works.

### 3.4 Connect them (CORS and WebSocket)

Browsers only call the backend when its CORS policy names the page's origin. Put the Vercel address in the backend:

```
CORS_ALLOWED_ORIGINS = https://<your-project>.vercel.app
```

Save it on Render (it redeploys). The same setting guards the STOMP WebSocket endpoint `/ws`. If you add a custom domain
or want Vercel preview URLs to work, list several origins separated by commas, for example
`https://testforge.vercel.app,https://*.vercel.app` (patterns with `*` are allowed).

Changing `VITE_*` values needs a **redeploy** of the frontend; they are not read at runtime.

## 4. Smoke verification

```powershell
.\scripts\smoke-test.ps1 -ApiUrl https://<service>.onrender.com -FrontendUrl https://<project>.vercel.app
```

It registers a throw-away user and checks: backend health, 401 without a token, register and login, creating a project,
suite and test case, listing, all five dashboard endpoints, the CORS preflight for the frontend origin, a WebSocket
handshake from that origin, the frontend page and the SPA deep-link fallback. It exits with code 1 if anything fails.

Then check by hand in the browser: sign in, open Dashboard, create a test case, start a manual run and watch the status
badges update. Accounts from your local database do not exist on the hosted one, so register a new user. To get demo data on
the hosted database:

```powershell
psql "postgresql://USER:PASSWORD@HOST/DB?sslmode=require" -v username=YOUR_USERNAME -f docs/demo-data.sql
```

## 5. Free-tier limits to expect

(Check each provider's pricing page; these change.)

* **Render free web service** sleeps after a period without traffic. The next request waits roughly a minute while the
  container starts, and the first WebSocket connection after a sleep can fail until the backend is awake. The instance has
  little memory, which is why the deployed image uses `WITH_AUTOMATION=false`.
* **Automated browser runs** (Selenium + Chromium) need much more memory than a free instance offers. In the hosted
  version, manual runs, dashboards and defects work; an automated run will fail. Run the
  full stack with `docker compose --profile full up` (or a paid instance with `WITH_AUTOMATION=true`) to use them.
* **Neon free** databases suspend when idle and wake on the next query, which adds a short delay to the first request.
* **Vercel hobby** is intended for non-commercial projects, which fits a portfolio.

## 6. Troubleshooting

| Symptom                                                  | Likely cause and fix                                                                          |
|----------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| Browser console: *blocked by CORS policy*                | `CORS_ALLOWED_ORIGINS` is missing the exact origin (scheme + host, no trailing slash)         |
| Live run log never connects                              | Same setting, or `VITE_API_URL` is `http://` while the page is `https://` (use `https://`)    |
| Backend exits at start with `Could not resolve placeholder` | A required variable (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`) is not set      |
| `Illegal base64 character` at start                      | `JWT_SECRET` is not Base64                                                                    |
| Flyway: *Unable to obtain connection / timeout*          | Using the Neon `-pooler` host or missing `?sslmode=require`; use the direct host              |
| Refreshing `/dashboard` gives 404                        | Missing SPA fallback: `frontend/vercel.json` (Vercel) or `nginx.conf` (Docker)                |
| Everything is empty after login                          | Fresh database: create a project, or load `docs/demo-data.sql`                                |
