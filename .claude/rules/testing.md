---
description: Testing approach, frameworks, and guidelines
paths:
  - "app/src/{test,testGplay,screenshotTest,screenshotTestGplayDebug}/**/*.{kt,java}"
---

# Testing

- Unit tests use JUnit, Mockk, and Kotest
- Hilt testing support is configured
- Test coverage is limited - add tests when modifying existing code
- No `androidTest` (instrumented emulator tests). Compose UI is covered via the `screenshotTest` source set (Android Gradle Plugin alpha screenshot validation API, see `app/build.gradle.kts`).
- Prefer plain JVM unit tests. Robolectric is still available (`org.robolectric:robolectric`) and acceptable when a test genuinely needs Android framework behavior (Room, `ApplicationProvider`, resource loading). Don't reach for Robolectric for pure-logic widget/ViewModel tests.
