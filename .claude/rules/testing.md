---
description: Testing approach, frameworks, and guidelines
globs:
  - "app/src/test/**/*.kt"
  - "app/src/testFossDebug/**/*.kt"
  - "app/src/testGplayDebug/**/*.kt"
---

# Testing

- Unit tests use JUnit, Mockk, and Kotest
- Hilt testing support is configured
- Test coverage is limited - add tests when modifying existing code
- Omit `androidTest`, we are not doing UI testing.
