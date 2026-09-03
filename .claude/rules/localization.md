---
description: String resource layout and the Crowdin translation flow
paths:
  - "app/src/{main,foss,gplay}/res/values*/strings.xml"
  - "fastlane/metadata/android/**"
---

# Localization

- All user facing strings should be extracted to `values/strings.xml`.
- String resources are spread across:
  - Common: `app/src/main/res/values*/strings.xml` (77 translated locales + base `values/`)
  - FOSS flavor: `app/src/foss/res/values*/strings.xml`
  - Google Play flavor: `app/src/gplay/res/values*/strings.xml`

## Translation Workflow

Translations come from Crowdin (project `879504`, see `crowdin.yaml` / `crowdin.sh`), not
from you. Write the English string in the base `values/strings.xml` and stop there.

- Do not hand-write or fan out sub-agents to produce per-locale translations.
- To pull and validate incoming translations, use the `android-translation:crowdin-pull`
  skill; to fill gaps on Crowdin itself, use `android-translation:crowdin-translate`.
- String context, character limits and file context are managed on Crowdin through the
  android-translation plugin's `crowdin-annotate` skill. XML comments in
  `values/strings.xml` no longer reach translators once a string's context has been written
  on Crowdin; change it there.
