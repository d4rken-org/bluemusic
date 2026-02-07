---
description: Architecture patterns, navigation, ViewModels, base classes, error handling, permissions
globs:
  - "app/src/main/java/**/*.kt"
  - "app/src/main/java/**/*.java"
---

# Architecture

- Package by feature, not by layer
- Each feature package (if it has UI components and logic components), should have a `core` and a `ui` package.
- Use Dagger/Hilt for dependency injection
- UI that can be navigated to are "Screen"s and each screen has a corresponding ViewModel
    - "Sub-screens" are called "Pages"
- New classes should be injected if possible (using Dagger/Hilt)
- Classes should have a companion object with an appropriate `val TAG = logTag("toplevel","sublevel")` entry.
- Use the Logging framework where appropriate to make future debugging easier (`Logging.kt`).

## Navigation

- The app uses Navigation3 (alpha) for Compose screens (`androidx.navigation3:navigation3-*`).
- The main directions are inside the `Nav` file (`common/navigation/Nav.kt`).
- Each screen should have its own navigation entry providing class (extending `NavigationEntry`).

## ViewModel Pattern

- Extend `BaseViewModel<State, Event>` (or `ViewModel1` through `ViewModel4` variants)
- Use `@HiltViewModel` annotation
- State management via `StateFlow`
- Events via `Channel<Event>`
- Example: `class MyViewModel @Inject constructor(...) : BaseViewModel<MyState, MyEvent>()`

## Key Packages

- `eu.darken.bluemusic.bluetooth`: Bluetooth device discovery and management
- `eu.darken.bluemusic.devices`: Device management and UI
- `eu.darken.bluemusic.main`: Main app navigation and settings
- `eu.darken.bluemusic.common`: Shared utilities and base classes
- `eu.darken.bluemusic.monitor`: Background monitoring and audio management
- `eu.darken.bluemusic.upgrade`: Premium features (flavor-specific)

## Common Base Classes

- **ViewModels**: Extend `BaseViewModel<State, Event>` from `common/architecture/`
- **Services**: Extend `Service2` for lifecycle-aware services
- **Activities**: Extend `Activity2` for common functionality
- **Compose Previews**: Use `@Preview2` with `PreviewWrapper`

## Error Handling

- Use `ErrorEventHandler` for UI error display
- Throw `UserFacingException` for user-visible errors
- All exceptions should have localizable messages

## Permissions

- Bluetooth permissions are handled in `PermissionHelper`
- Check permissions before Bluetooth operations
- Handle permission denial gracefully
