---
description: Gradle build, test, and lint commands
globs:
  - "build.gradle*"
  - "app/build.gradle*"
  - "settings.gradle*"
  - "gradle/**"
---

# Build Commands

```bash
# Build commands
./gradlew assembleFossDebug      # FOSS debug build
./gradlew assembleGplayRelease   # Google Play release build
./gradlew installFossDebug       # Install FOSS debug on device

# Testing
./gradlew test                   # Run all unit tests
./gradlew testFossDebugUnitTest  # Run FOSS debug unit tests

# Code quality
./gradlew lint                   # Run lint checks
./gradlew lintFix               # Run lint with auto-fix
./gradlew ktlintCheck           # Run ktlint checks (if configured)
./gradlew ktlintFormat          # Run ktlint format (if configured)
```

## Tips

- Prefer running builds and lint in sub-agents to keep the main context window clean.
- Use `assembleFossDebug` for quick iteration; it's the fastest build variant.
