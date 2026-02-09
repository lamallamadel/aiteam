---
name: testing
description: >
  Write, review, and improve tests for Java/Spring Boot (JUnit 5, Mockito, TestContainers),
  Angular (Jasmine/Karma, Jest), and E2E (Cypress, Playwright). Use this skill when the user asks to:
  (1) Write unit, integration, or e2e tests for existing code,
  (2) Review existing tests for quality and coverage gaps,
  (3) Fix failing tests or flaky tests,
  (4) Set up testing infrastructure or configuration,
  (5) Improve test coverage or test design.
  Triggers: "write tests", "unit test", "integration test", "e2e test", "test coverage",
  "fix this test", "flaky test", "mock this", "test for this service/component",
  "JUnit", "Mockito", "Jasmine", "Karma", "Jest", "Cypress", "Playwright", "TestContainers".
---

# Testing Skill

## Workflow

1. **Identify scope**: Unit, integration, or e2e test.
2. **Detect stack**: Java/Spring → JUnit 5 + Mockito (+ TestContainers for integration). Angular → Jasmine/Karma or Jest. E2E → Cypress or Playwright.
3. **Analyze code under test**: Identify all code paths, edge cases, error conditions.
4. **Write/review tests**: Follow patterns in `references/testing-patterns.md`.
5. **Output**: Complete test file(s) + brief explanation of what is covered.

## Test Writing Rules

### Structure: Arrange-Act-Assert (AAA)
Every test follows this structure. Use blank lines to separate the three sections.

### Naming convention
- Java: `should_[expected]_when_[condition]()` — e.g., `should_throwException_when_userNotFound()`
- Angular/JS: `should [expected] when [condition]` — e.g., `'should display error when form is invalid'`

### What to test

**Unit tests**: One class/function in isolation. Mock all dependencies.
- Every public method
- Happy path + at least one error/edge case per method
- Boundary values (null, empty, zero, max)
- Exception cases

**Integration tests**: Multiple components working together.
- Java: real DB (TestContainers), real Spring context (`@SpringBootTest`), real HTTP (`MockMvc` or `WebTestClient`)
- Angular: Component + service with `HttpClientTestingModule`
- API contract tests: request/response shape validation

**E2E tests**: User journeys through the full application.
- Critical user flows (login, CRUD, checkout)
- Cross-browser if needed
- Avoid testing implementation details — test user-visible behavior

### What NOT to test
- Private methods directly (test through public API)
- Framework code (Spring, Angular internals)
- Getters/setters with no logic
- Third-party libraries

## General Rules

- Tests must be **independent**: no shared mutable state, no order dependency.
- Tests must be **fast**: mock external dependencies in unit tests. Reserve real services for integration tests.
- Tests must be **readable**: test name describes the scenario, assertions are clear.
- Use `@DisplayName` (JUnit 5) or `describe`/`it` blocks (JS) for readable output.
- One logical assertion per test (multiple `assertThat` calls on the same result object is fine).
- For Angular: prefer `fakeAsync`/`tick` over real `async` for deterministic timing.
- For Cypress/Playwright: use `data-testid` attributes for selectors, never CSS classes.
- Flag untestable code (too many dependencies, side effects) as a design issue.
