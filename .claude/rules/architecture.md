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
- Each screen implements `NavigationEntry` and is registered via a Hilt module (`@Module @InstallIn(SingletonComponent::class)`) using `@Binds @IntoSet`, then consumed as `Set<NavigationEntry>` in `MainActivity`. Reference: `bluetooth/ui/discover/DiscoverNavigation.kt`.

## ViewModel Pattern

- Extend `ViewModel1`–`ViewModel4` from `common/ui/` (pick by capability: `ViewModel2` adds StateFlow helpers, `ViewModel3` adds `errorEvents: SingleEventFlow<Throwable>`, `ViewModel4` adds `NavigationController` integration).
- `BaseViewModel` in `common/architecture/` is legacy/unused — do not extend it.
- Use `@HiltViewModel`; inject `DispatcherProvider` for coroutine contexts.
- State via `StateFlow`. Error events via `SingleEventFlow<Throwable>` exposed by `ViewModel3+`. Custom events: prefer `SingleEventFlow<T>`; if using `Channel<T>` directly, keep it private and expose via `receiveAsFlow()` — never expose a raw `Channel`.
- For ViewModels that need nav arguments, use `@AssistedInject` with a Hilt factory (reference: `devices/ui/appselection/AppSelectionViewModel.kt`).
- Example: `class MyViewModel @Inject constructor(dispatcherProvider: DispatcherProvider, ...) : ViewModel3(dispatcherProvider)`

## Key Packages

- `eu.darken.bluemusic.bluetooth`: Bluetooth device discovery and management
- `eu.darken.bluemusic.devices`: Device management and UI
- `eu.darken.bluemusic.main`: Main app navigation and settings
- `eu.darken.bluemusic.common`: Shared utilities and base classes
- `eu.darken.bluemusic.monitor`: Background monitoring and audio management
- `eu.darken.bluemusic.upgrade`: Premium features (flavor-specific)

## Common Base Classes

- **ViewModels**: Extend `ViewModel1`–`ViewModel4` from `common/ui/`. `BaseViewModel` in `common/architecture/` is legacy.
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
