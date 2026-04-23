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

- Prefer running builds and lint in sub-agents to keep the main context window clean.
- Use `assembleFossDebug` for quick iteration; it's the fastest build variant.
