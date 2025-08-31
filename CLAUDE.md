# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

BlueMusic is an Android application that manages individual music and voice volumes for each Bluetooth device. The app is distributed on
both GitHub (FOSS) and Google Play Store, with different build flavors for each platform.

## Build Commands

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

## Architecture

- Package by feature, not by layer
- Each feature package (if it has UI components and logic components), should have a `core` and a `ui` package.
- Use Dagger/Hilt for dependency injection
- UI that can be navigated to are "Screen"s and each screen has a corresponding ViewModel
    - "Sub-screens" are called "Pages"
- The app uses Navigation3 (alpha) for Compose screens (`androidx.navigation3:navigation3-*`).
- The main directions are inside the `Nav` file (`common/navigation/Nav.kt`).
- Each screen should have it's own navigation entry providing class (extending `NavigationEntry`).
- New classes should be injected if possible (using Dagger/Hilt)
- Classes should have a companion object with an appropriate `val TAG = logTag("toplevel","sublevel")` entry.
- Use the Logging framework where appropriate to make future debugging easier (`Logging.kt`).
- There are two different build flavors - `foss` (GitHub) and `gplay` (Google Play with billing)

### ViewModel Pattern

- Extend `BaseViewModel<State, Event>` (or `ViewModel1` through `ViewModel4` variants)
- Use `@HiltViewModel` annotation
- State management via `StateFlow`
- Events via `Channel<Event>`
- Example: `class MyViewModel @Inject constructor(...) : BaseViewModel<MyState, MyEvent>()`

### Key Packages

- `eu.darken.bluemusic.bluetooth`: Bluetooth device discovery and management
- `eu.darken.bluemusic.devices`: Device management and UI
- `eu.darken.bluemusic.main`: Main app navigation and settings
- `eu.darken.bluemusic.common`: Shared utilities and base classes
- `eu.darken.bluemusic.monitor`: Background monitoring and audio management
- `eu.darken.bluemusic.upgrade`: Premium features (flavor-specific)

## Testing Approach

- Unit tests use JUnit, Mockk, and Kotest
- Hilt testing support is configured
- Test coverage is limited - add tests when modifying existing code
- Omit `androidTest`, we are not doing UI testing.

## Agent instructions

- When localizing texts, create a TODO for each locale you need to provide translations for.
- Use sub-agents, one agent per TODO (if possible).

## Code Style

- Kotlin-first codebase
- Use coroutines and Flow for async operations
- Follow existing patterns in the codebase
- Prefer immutable data classes for state
- When writing user facing texts, prefer informal and casual language.
- Use Hilt for dependency injection in new code
- Do NOT add comments on obvious code
- Prefer exposing fewer fields and functions and enabling specific functionality via extension functions
- All user facing strings should be extract to `values/strings.xml` and translated for all other languages too.
- `@OptIn(ExperimentalMaterial3Api::class)` is not required
- Create previews for all UI components using the `@Preview2` annotation and `PreviewWrapper`
- Prefer early returns to reduce code nesting
- Use `@Stable` and `@Immutable` annotations where appropriate for Compose performance
- Prefer `StateFlow` over `LiveData` for new code
- Use `collectAsStateWithLifecycle()` when collecting flows in Compose

## Quick Reference

### Key Files and Locations

- **Main Activity**: `app/src/main/java/eu/darken/bluemusic/main/ui/MainActivity.kt`
- **Application Class**: `app/src/main/java/eu/darken/bluemusic/App.kt`
- **Navigation Routes**: `app/src/main/java/eu/darken/bluemusic/common/navigation/Nav.kt`
- **String Resources**:
  - Common: `app/src/main/res/values*/strings.xml` (44+ locales)
  - FOSS flavor: `app/src/foss/res/values*/strings.xml`
  - Google Play flavor: `app/src/gplay/res/values*/strings.xml`
- **Database (Room)**: `app/src/main/java/eu/darken/bluemusic/devices/core/database/DevicesRoomDb.kt`

### Common Base Classes

- **ViewModels**: Extend `BaseViewModel<State, Event>` from `common/architecture/`
- **Services**: Extend `Service2` for lifecycle-aware services
- **Activities**: Extend `Activity2` for common functionality
- **Compose Previews**: Use `@Preview2` with `PreviewWrapper`

### Error Handling

- Use `ErrorEventHandler` for UI error display
- Throw `UserFacingException` for user-visible errors
- All exceptions should have localizable messages

### Permissions

- Bluetooth permissions are handled in `PermissionHelper`
- Check permissions before Bluetooth operations
- Handle permission denial gracefully