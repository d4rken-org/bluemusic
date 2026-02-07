---
description: Git commit message format and conventions
---

# Commit Guidelines

## Format

```
<Action> <what was changed>
```

Action-oriented title, imperative mood, no period at the end.

## Common Prefixes

- `Add` - New feature or functionality (e.g., "Add connection alert feature for Bluetooth devices")
- `Fix` - Bug fix (e.g., "Fix edge-to-edge display for navigation bar")
- `Update` - Dependency or config update (e.g., "Update Android Gradle Plugin to 8.13.1")
- `Remove` - Removing code or features (e.g., "Remove Realm database and legacy migration code")
- `Bump` - Version bumps (e.g., "Bump Kotlin, Dagger, and AGP versions")
- `Hide` - UI visibility changes (e.g., "Hide FAB on scroll in Dashboard screen")
- `Raise` - Increasing minimums (e.g., "Raise minSdk to 23")
- `Release:` - Release tags (e.g., "Release: 3.1.1-rc0")

## Rules

- Keep the title under 72 characters
- Be specific about what changed and where
- No need for a body unless the change is complex
