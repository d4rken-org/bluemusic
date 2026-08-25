# Store screenshots

Play Store screenshots are rendered from Compose previews, not captured from a running app. No
device or emulator is involved.

- `app/src/debug/java/eu/darken/bluemusic/screenshots/ScreenshotContent.kt` — the composables, one
  per screenshot, with fixed demo state.
- `app/src/screenshotTest/kotlin/.../PlayStoreScreenshots.kt` — binds each composable to a locale
  set.
- `app/src/screenshotTest/kotlin/.../PlayStoreLocales.kt` — the locale set: `@Preview(name = …)` is
  the fastlane directory, `locale = …` the Android resource qualifier.

## The locale set

`PlayStoreLocales` covers `fastlane/metadata/android/*` intersected with the languages Play accepts
for a store listing. Play rejects an upload that carries a language it has no listing for, so the
Fastfile filters the tree to the same set before handing it to `supply`. Adding a locale to one
list without the other either wastes a render or ships a listing with no screenshots.

Locales the repo carries that Play does not accept (`eo`, `es-AR`, `kmr-TR`, `pcm-NG`, `sc-IT`,
`sq-AL`, `tl-PH`, `ur-IN`, `uz`) still get their text listing on F-Droid, which reads this same
tree.

## Regenerating

```bash
./fastlane/generate_screenshots.sh                 # all locales
./fastlane/generate_screenshots.sh --smoke         # 6 locales, for iterating on a composable
./fastlane/generate_screenshots.sh --batch-size 10 # fewer, larger Gradle invocations
./fastlane/copy_screenshots.sh                     # reference renders -> fastlane/metadata
```

`generate_screenshots.sh` rewrites `PlayStoreLocales.kt` one batch at a time and restores it on
exit; the batching keeps the renderer's heap use bounded within a single Gradle run. It logs a
warning and continues when a batch fails, so check that
`fastlane/metadata/android/<locale>/images/phoneScreenshots/` has 8 files per locale afterwards
rather than trusting the exit code.

`copy_screenshots.sh` skips `DeviceConfigTiming` — it is rendered to catch layout breakage in the
timing card, not published.

## Why only en-US is committed

The renders are reproducible, and a full set is ~80MB of PNGs. Committing them on every
regeneration grows the history by that much each time, so `.gitignore` keeps only `en-US`, which
`README.md` hotlinks from the default branch.

**Regenerate before uploading screenshots.** A fresh clone has none, and `supply` silently skips a
locale with no local screenshots rather than failing, so the upload would quietly refresh en-US
alone and leave every other language stale. The Fastfile prints `Play locales with no screenshots:`
when that is about to happen.

## Uploading

```bash
cd fastlane
bundle exec fastlane validate_listing    # dry run, changes nothing on the store
bundle exec fastlane listing_only        # title + short/full descriptions
bundle exec fastlane screenshots_only    # screenshots -> production listing
```

**`listing_only` has to run before `screenshots_only`.** Play refuses to attach a screenshot to a
language that has no listing yet, and `screenshots_only` skips metadata, so for a locale being
published for the first time the title is never sent and the whole edit is rejected with
`This app has no title for language <locale>`. Uploading the text first creates the listing.

`validate_listing` does not catch this, because it sends metadata and screenshots in one edit and
the title is therefore present. It checks that the content is acceptable, not that the two upload
lanes are ordered correctly.

Play edits are transactional: a run that dies partway commits nothing, so a transient API error is
safe to just re-run.
