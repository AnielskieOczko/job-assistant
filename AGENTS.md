# AGENTS.md

Project architecture, module boundaries, invariants, commands, and testing conventions live in [`CLAUDE.md`](CLAUDE.md). Read it before making changes.

## Development Workflow

- **Use TDD (Test-Driven Development):** For new features and bug fixes, write the failing test(s) first, verify they fail for the expected reason, then implement the production code until they pass.
- **Keep tests focused, not exhaustive:** Do not write tests for everything (trivial getters, DTOs, boilerplate, or pass-through wiring). Focus tests on:
  - Key classes, core domain logic, and non-trivial pure functions
  - Architectural invariants (`CvInvariant`, `PromptPrivacyInvariant`, `ModularityTest`, `ApiContractTest`)
  - Critical integration boundaries and failure modes
