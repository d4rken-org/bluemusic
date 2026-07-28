---
description: Gradle build, test, and lint commands
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

# Screenshot testing (Compose screenshot validation, alpha API)
./gradlew validateScreenshotTest           # Validate against golden images (all variants)
./gradlew updateScreenshotTest             # Regenerate golden images (all variants)
./gradlew validateFossDebugScreenshotTest  # Validate FOSS debug variant only
./gradlew updateFossDebugScreenshotTest    # Regenerate FOSS debug goldens only

# Code quality
./gradlew lint                   # Run lint checks
./gradlew lintFix                # Run lint with auto-fix
```

## Tips

- Run builds, tests, and lint through the `devtools:build-runner` agent. It keeps verbose
  output out of the main context and returns a pass/fail summary with the exact error lines.
  Do not tail the log inline instead — on a gradle failure the last N lines are the
  `BUILD FAILED` banner, not the compile error that caused it.
- Use `assembleFossDebug` for quick iteration; it's the fastest build variant.
