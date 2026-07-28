# BlueMusic

Android app that manages individual music and voice volumes for each Bluetooth device.
Distributed on GitHub (FOSS) and Google Play Store with separate build flavors.

## Build Flavors

| Flavor | Distribution | Notes |
|--------|-------------|-------|
| `foss` | GitHub | No proprietary dependencies |
| `gplay` | Google Play | Includes billing for premium features |

Use `assembleFossDebug` for quick iteration. See `rules/build-commands.md` for all commands.

## Key Files

| What | Where |
|------|-------|
| Main Activity | `app/src/main/java/eu/darken/bluemusic/main/ui/MainActivity.kt` |
| Application Class | `app/src/main/java/eu/darken/bluemusic/App.kt` |
| Navigation Routes | `app/src/main/java/eu/darken/bluemusic/common/navigation/Nav.kt` |
| Database (Room) | `app/src/main/java/eu/darken/bluemusic/devices/core/database/DevicesRoomDb.kt` |
| String Resources | `app/src/main/res/values*/strings.xml` (77 translated locales + base) |
| FOSS Strings | `app/src/foss/res/values*/strings.xml` |
| GPlay Strings | `app/src/gplay/res/values*/strings.xml` |
| Build Config | `app/build.gradle.kts` |

## Rules

Detailed guidance is in `.claude/rules/`:

- **[architecture.md](rules/architecture.md)** - Package structure, Navigation3, ViewModel pattern, base classes, error handling, permissions
- **[code-style.md](rules/code-style.md)** - Kotlin conventions, Compose patterns, state management
- **[testing.md](rules/testing.md)** - JUnit/Mockk/Kotest, screenshot tests; no instrumented androidTest
- **[localization.md](rules/localization.md)** - String extraction, 77 locales, Crowdin flow
- **[build-commands.md](rules/build-commands.md)** - All gradlew commands
- **[release.md](rules/release.md)** - Release flow, version bumping, and tag validation
- **[commit-guidelines.md](rules/commit-guidelines.md)** - Commit message format and conventions
- **[agent-instructions.md](rules/agent-instructions.md)** - When to delegate to a sub-agent

`architecture.md`, `code-style.md`, `testing.md`, `localization.md`, and `release.md` are
path-scoped via `paths:` frontmatter — they load only when you touch matching files. The
rest load every session.

## Working Style

- Before the first tool call, say in one sentence what you're about to do. While working,
  give a brief update only when you find something important or change direction. Lead the
  final message with the outcome.
- Match written artifacts — PR descriptions, plan docs, reports — to what the task needs.
  Cover the substance; no filler sections or redundant summaries.

## Dev Tips

- The FOSS debug variant (`assembleFossDebug`) builds fastest for iteration.
- Check `rules/architecture.md` before adding new screens or ViewModels.
- Write user-facing strings in the base `values/strings.xml` only — translations come from
  Crowdin, never from you.
- No `androidTest` in this project; Compose UI is covered by the `screenshotTest` source set.
