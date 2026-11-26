![Tests](https://img.shields.io/badge/tests-274%20passing-brightgreen) ![Coverage](https://img.shields.io/badge/service%20%2B%20controller%20coverage-96%25%E2%80%9399%25-brightgreen)

# testforge

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