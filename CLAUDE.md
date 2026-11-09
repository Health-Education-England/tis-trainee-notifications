# CLAUDE.md — AI Agent Instructions

This file provides context and rules for AI agents working on this codebase.

## Project Overview

**tis-trainee-notifications** is a Spring Boot 3.x service that manages and sends
notifications (email and in-app) to NHS trainees based on events from TIS
Self-Service. It uses an event-driven architecture consuming messages from AWS SQS
queues, persists data in MongoDB, caches in Redis, and sends emails via AWS SES.

## Architecture — Respect It

All changes must follow the established layered architecture. Do not introduce new
patterns, frameworks, or structural deviations without explicit instruction.

### Package Structure

```
uk.nhs.tis.trainee.notifications
├── api/            → REST controllers (minimal; most input is event-driven)
├── config/         → Spring @Configuration and @ConfigurationProperties beans
├── dto/            → Data Transfer Objects (records for events and API payloads)
├── event/          → SQS listeners (@SqsListener methods); thin, delegate to services
├── job/            → Scheduled jobs (use @SchedulerLock for distributed safety)
├── mapper/         → MapStruct interfaces for DTO ↔ model mapping
├── migration/      → Mongock database migration changesets
├── model/          → Domain entities (Lombok @Data, persisted in MongoDB)
├── repository/     → Spring Data MongoDB repositories
├── service/        → Business logic; orchestration lives here
```

### Key Conventions

| Concern | Convention |
|---------|-----------|
| Dependency injection | Constructor injection (implicit, no `@Autowired`) |
| Boilerplate reduction | Lombok (`@Data`, `@Slf4j`, `@RequiredArgsConstructor`) |
| Object mapping | MapStruct (never manual field-by-field copying) |
| Event consumption | `@SqsListener` in `event/` package; listeners must be thin (validate + delegate) |
| Scheduling | ShedLock (`@SchedulerLock`) on all `@Scheduled` methods |
| Database migrations | Mongock changesets in `migration/` package |
| Configuration | Externalised via `application.yml`; injected with `@Value` or `@ConfigurationProperties` |
| Template rendering | Thymeleaf templates in `src/main/resources/templates/` |
| Caching | Spring Cache abstraction backed by Redis |

### What NOT to do

- Do not add new Spring profiles unless explicitly asked.
- Do not replace MongoDB with another datastore.
- Do not introduce synchronous inter-service HTTP calls where async SQS already exists.
- Do not bypass the mapper layer by manually mapping DTOs to models.
- Do not add business logic to event listeners — keep them thin.
- Do not add new dependencies without justification; prefer what's already on the classpath.

## Testing Requirements

**Every change must be accompanied by tests.** No exceptions.

### Unit Tests

- Located in `src/test/java/` mirroring the main source structure.
- Use **JUnit 5** (Jupiter) and **Mockito** for mocking.
- Mock all collaborators; test one class in isolation.
- Use `@ExtendWith(MockitoExtension.class)` — not `@SpringBootTest`.
- Use parameterised tests (`@ParameterizedTest`) when testing multiple input variants.
- Assert behaviour, not implementation details.

### Integration Tests

- Class names end with `IntegrationTest`.
- Use `@SpringBootTest` with **Testcontainers** (MongoDB, LocalStack, embedded Redis).
- Verify end-to-end flows: message in → processing → side effects (DB writes, emails, SNS publishes).
- Use **Awaitility** for asserting asynchronous outcomes.
- Use **Jsoup** for asserting rendered HTML email content.

### Coverage

- JaCoCo is configured; aim for ≥80% line coverage on new/changed code.
- All public service methods must have both unit and integration test coverage.

## Software Development Principles

Follow these principles in all code changes:

### DRY (Don't Repeat Yourself)

- Extract shared logic into private helper methods or dedicated service classes.
- Reuse existing MapStruct mappers — do not duplicate mapping logic.
- If the same template enrichment or validation appears in multiple places, consolidate it.

### YAGNI (You Aren't Gonna Need It)

- Do not add methods, fields, parameters, or configuration "just in case."
- Implement only what is required to satisfy the current requirement.
- Do not create abstractions (interfaces, base classes) unless there are at least two concrete implementations today.

### Encapsulation over Coupling

- Keep fields private. Expose behaviour through methods, not data.
- Services should depend on interfaces or narrowly-scoped collaborators, not on the internals of other services.
- DTOs crossing package boundaries must be immutable records.
- Do not expose repository methods directly to controllers or listeners — always go through a service.

### Additional Principles

- **Single Responsibility**: each class has one reason to change.
- **Fail Fast**: validate inputs at the boundary (listeners, controllers) and throw meaningful exceptions early.
- **Favour composition over inheritance**: use injected collaborators, not deep class hierarchies.
- **Idempotency**: SQS messages may be delivered more than once; design handlers to be safe on replay.

## Code Style

- **Google Java Style** enforced via Checkstyle (configured in `build.gradle.kts`).
- 2-space indentation, 4-space continuation indent (see `.editorconfig`).
- UTF-8 encoding, LF line endings, final newline required.
- Maximum line length per Google style (100 characters).

## Build & Verification

Before considering any change complete, ensure:

```shell
./gradlew check
```

This runs Checkstyle, all unit and integration tests, and JaCoCo coverage.

## Domain Vocabulary

Use these terms consistently in code, comments, and documentation:

| Term | Meaning |
|------|---------|
| Trainee | An NHS trainee doctor receiving notifications |
| Programme Membership | A trainee's assignment to a training programme |
| Placement | A trainee's clinical placement at a site |
| Conditions of Joining (CoJ) | Regulatory conditions a trainee must acknowledge |
| LTFT | Less Than Full Time — a flexible training arrangement |
| In-App notification | A notification displayed within TIS Self-Service UI |
| Milestone | A date-relative trigger point for a notification (e.g., 12 weeks before start) |

## Summary

1. Respect the existing architecture and package structure.
2. Back every change with unit tests AND integration tests.
3. Follow DRY, YAGNI, and encapsulation principles.
4. Keep listeners thin, services focused, and mappers in MapStruct.
5. Run `./gradlew check` to validate before finishing.

