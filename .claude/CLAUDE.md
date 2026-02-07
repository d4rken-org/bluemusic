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
| String Resources | `app/src/main/res/values*/strings.xml` (44+ locales) |
| FOSS Strings | `app/src/foss/res/values*/strings.xml` |
| GPlay Strings | `app/src/gplay/res/values*/strings.xml` |
| Build Config | `app/build.gradle.kts` |

## Rules

Detailed guidance is in `.claude/rules/`:

- **[architecture.md](rules/architecture.md)** - Package structure, Navigation3, ViewModel pattern, base classes, error handling, permissions
- **[code-style.md](rules/code-style.md)** - Kotlin conventions, Compose patterns, state management
- **[testing.md](rules/testing.md)** - JUnit/Mockk/Kotest, no androidTest
- **[localization.md](rules/localization.md)** - String extraction, 44+ locales, translation workflow
- **[build-commands.md](rules/build-commands.md)** - All gradlew commands
- **[commit-guidelines.md](rules/commit-guidelines.md)** - Commit message format and conventions
- **[agent-instructions.md](rules/agent-instructions.md)** - Sub-agent delegation, exploring vs implementing

## Dev Tips

- Run builds and lint checks in sub-agents to keep the main context window clean.
- The FOSS debug variant (`assembleFossDebug`) builds fastest for iteration.
- Check `rules/architecture.md` before adding new screens or ViewModels.
- All user-facing strings must be localized (see `rules/localization.md`).
