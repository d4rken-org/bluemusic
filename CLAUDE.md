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
```

## Architecture

- Package by feature, not by layer
- Each feature package (if it has UI components and logic components), should have a `core` and a `ui` package.
- Use Dagger/Hilt for dependency injection
- Repository pattern with Room (migrating from Realm) and DataStore
- UI that can be navigated to are "Screen"s and each screen has a corresponding ViewModel
    - "Sub-screens" are called "Pages"

### Key Packages

- `eu.darken.bluemusic.bluetooth`: Bluetooth device discovery and management
- `eu.darken.bluemusic.devices`: Device management and UI
- `eu.darken.bluemusic.main`: Main app navigation and settings
- `eu.darken.bluemusic.common`: Shared utilities and base classes

### Navigation

The app uses Navigation3 (alpha) for Compose screens. Main entry point is `MainNavGraph` with destinations for devices, settings, and other
features.

## Development Notes

1. **Build Flavors**: Two flavors - `foss` (GitHub) and `gplay` (Google Play with billing)
2. **Database Migration**: Active migration from Realm to Room - be careful when modifying database code
3. **UI Migration**: Ongoing migration to Compose - prefer Compose for new UI components
4. **Permissions**: App requires Bluetooth permissions and notification permissions (Android 13+)
5. **Background Services**: Uses services for Bluetooth monitoring - handle lifecycle carefully

## Testing Approach

- Unit tests use JUnit, Mockk, and Kotest
- Hilt testing support is configured
- Test coverage is limited - add tests when modifying existing code

## Code Style

- Kotlin-first codebase
- Use coroutines and Flow for async operations
- Follow existing patterns in the codebase
- Prefer immutable data classes for state
- Use Hilt for dependency injection in new code
- Prefer exposing fewer fields and functions and enabling specific functionality via extension functions