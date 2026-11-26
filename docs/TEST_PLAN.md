# TestForge Test Plan

## 1. Purpose and scope
TestForge is a test-management platform (projects, suites, cases, runs, defects, dashboard). This plan describes how its **backend** is tested, what is covered, and what is deliberately left out. The frontend is covered separately (Section 9).

**In scope:** REST API, security (JWT, ownership rules), validation, persistence (Flyway schema, JPA queries, transactions), dashboard analytics, live-status messaging.
**Out of scope:** load/performance testing, browser compatibility, penetration testing.

## 2. Test levels

| Level | Tools | Database | Location |
|---|---|---|---|
| Unit (services, mappers, domain rules) | JUnit 5, Mockito, AssertJ | none (mocked) | `src/test/.../service`, `mapper`, `model`, `automation` |
| Repository / integration | Spring Boot test, Testcontainers PostgreSQL 15, JdbcTemplate | real PostgreSQL | `src/test/.../integration` |
| API (full stack, no network) | MockMvc + Spring Security + JsonPath | real PostgreSQL | `src/test/.../api` |
| Coverage | JaCoCo | n/a | `target/site/jacoco/index.html` |

PostgreSQL is used (not H2) because the dashboard relies on PostgreSQL features (`FILTER`, `lag()`, `to_char`) and Flyway migrations are PostgreSQL-specific.

## 3. Test approach per area

**Authentication & JWT** â€“ register/login, password stored as a hash, wrong password and unknown user rejected, missing / garbage / tampered / expired / wrongly-signed tokens give 401, `Bearer` scheme required, public endpoints (health, OpenAPI) stay open, every response carries `X-Request-Id`.

**Authorization** â€“ one rule: only a project's owner can read or change anything inside it. Every resource family (projects, suites, cases, runs, results, defects, dashboard) is tested with a second user and must return 403. A token that *claims* `ADMIN` must grant nothing.

**Validation** â€“ 400 with a `fieldErrors` map for bad bodies (including nested `steps[0].action`), malformed JSON, missing/ill-typed parameters, unknown enum values; 404 for unknown ids; 405/415 for wrong method / content type. All errors use the `ApiError` shape.

**Pagination & sorting** â€“ default page size and sort, last/over-the-end pages, ascending/descending sort, no overlap between pages, size cap, combined filter + paging.

**Persistence** â€“ Flyway versions 1â€“4 apply; indexes and constraints exist; cascade deletes (project â†’ everything) and `SET NULL` (result â†’ defect link); `@EntityGraph` loads exactly what responses need and a list of runs does not issue one query per row (N+1 guard); transactional workflows roll back completely.

**Business workflows** â€“ run creation snapshots active cases as `NOT_RUN`; run auto-completes / re-opens; one open defect per failed result; defect status transitions; test-case search by text, tags, priority, status.

**Dashboard** â€“ counts, pass-rate trend, severity grouping, flaky-test detection (flips between pass/fail), recent activity, all scoped per project.

## 4. Test data
Rows are inserted with plain SQL (`support/Db`) so each test controls every value, including timestamps. Each test starts from an empty database (`TRUNCATE ... RESTART IDENTITY CASCADE`) and caches are cleared. Tests never assert specific calendar dates (the JVM and the database container may be in different time zones).

## 5. Environment
- Java 17, Maven wrapper, **Docker Desktop running** (Testcontainers starts a throw-away PostgreSQL container; one container is shared by the whole run).
- Test profile: `src/test/resources/application-test.yml` (test-only JWT secret, quiet logging).

## 6. How to run
```powershell
cd backend
.\mvnw.cmd test                       # everything
.\mvnw.cmd test -Dtest=AuthApiTest    # one class
start target\site\jacoco\index.html   # coverage report (after a test run)
```

## 7. Entry / exit criteria
- **Entry:** code compiles, Docker is running.
- **Exit:** all tests pass; line coverage on `service` and `controller` packages at least 75%; no open Critical/High defect from the test run.

## 8. Risks and known gaps
| Risk | Mitigation / status |
|---|---|
| `sort=<unknown property>` could surface as HTTP 500 | Covered by `PaginationApiTest.sortingByAnUnknownPropertyIsAClientErrorNotAServerError`; fix is to map `PropertyReferenceException` to 400 |
| Ownership failures return 403 (not 404), so ids can be enumerated | Accepted for now; documented |
| Roles exist in the token but grant nothing | Tested so a future change cannot silently introduce privilege escalation |
| WebSocket live updates only unit-tested at service level | Manual check in the UI; automated STOMP test is a future item |
| No performance testing | Out of scope |

## 9. Other test assets
- API contract: `docs/openapi.json` (exported from the running backend) and Swagger UI at `/swagger-ui.html`.
- Automation suite for the demo app: `automation-suite/`.

## 10. Reporting
JUnit results: `backend/target/surefire-reports/`. Coverage: `backend/target/site/jacoco/`. Defects found by testing are logged in TestForge itself.
