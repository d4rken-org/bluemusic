---
description: Kotlin conventions, Compose patterns, state management
globs:
  - "app/src/main/java/**/*.kt"
  - "app/src/main/java/**/*.java"
---

# Code Style

- Kotlin-first codebase
- Use coroutines and Flow for async operations
- Follow existing patterns in the codebase
- Prefer immutable data classes for state
- When writing user facing texts, prefer informal and casual language.
- Use Hilt for dependency injection in new code
- Do NOT add comments on obvious code
- Prefer exposing fewer fields and functions and enabling specific functionality via extension functions
- Prefer early returns to reduce code nesting

## Compose

- `@OptIn(ExperimentalMaterial3Api::class)` is not required
- Create previews for all UI components using the `@Preview2` annotation and `PreviewWrapper`
- Use `@Stable` and `@Immutable` annotations where appropriate for Compose performance
- Prefer `StateFlow` over `LiveData` for new code
- Use `collectAsStateWithLifecycle()` when collecting flows in Compose
