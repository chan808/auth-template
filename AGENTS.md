# Project Rules

- Backend stack: Kotlin, Spring Boot, Spring Security, JPA.
- Structure: feature code stays inside its module; shared utilities only in `common`; cross-module communication only via exposed APIs or events.
- Architecture: keep module boundaries verifiable with Spring Modulith or ArchUnit when dependencies change.
- Layers: controllers handle HTTP only; application coordinates use cases, transactions, and external calls; domain holds core business rules.
- Persistence: if the project uses a relational DB, all schema changes go through Flyway migrations; never change schema outside migrations.
- Style: prefer constructor injection, immutable DTOs, explicit names, and small functions.
- Comments: only when necessary; one line explaining purpose. Example: `// 토큰 검증 등 읽기 전용 auth 연산`
- Errors, logging, responses: follow existing `ApiResponse`, exception, and logging patterns.
- Tests: use JUnit 5, MockK, springmockk, and Testcontainers. Prefer unit tests first; use `@WebMvcTest` for controller and security flow; integration tests only for Redis or DB behavior and tag them `integration`.
