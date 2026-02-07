---
description: String resource extraction, locales, and translation workflow
globs:
  - "app/src/main/res/values*/strings.xml"
  - "app/src/foss/res/values*/strings.xml"
  - "app/src/gplay/res/values*/strings.xml"
---

# Localization

- All user facing strings should be extracted to `values/strings.xml` and translated for all other languages too.
- String resources are spread across:
  - Common: `app/src/main/res/values*/strings.xml` (44+ locales)
  - FOSS flavor: `app/src/foss/res/values*/strings.xml`
  - Google Play flavor: `app/src/gplay/res/values*/strings.xml`

## Translation Workflow

- When localizing texts, create a TODO for each locale you need to provide translations for.
- Use sub-agents, one agent per TODO (if possible).
