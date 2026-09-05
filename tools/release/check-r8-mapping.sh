#!/usr/bin/env bash
# Verifies that the gplay flavor is obfuscated and the foss flavor is not, and that the
# classes resolved by name at runtime kept their original names.
#
# Run from the repository root after:
#   ./gradlew assembleGplayRelease assembleFossRelease
#
# mapping.txt is ~80 MB, so it is only ever grepped/awked, never read as a whole.
set -euo pipefail

FOSS_CONFIG="app/build/outputs/mapping/fossRelease/configuration.txt"
GPLAY_CONFIG="app/build/outputs/mapping/gplayRelease/configuration.txt"
GPLAY_MAPPING="app/build/outputs/mapping/gplayRelease/mapping.txt"

for f in "$FOSS_CONFIG" "$GPLAY_CONFIG" "$GPLAY_MAPPING"; do
    if [ ! -f "$f" ]; then
        echo "FAIL: missing $f - run ./gradlew assembleGplayRelease assembleFossRelease first" >&2
        exit 1
    fi
done

if ! grep -qx -- "-dontobfuscate" "$FOSS_CONFIG"; then
    echo "FAIL: foss release is missing -dontobfuscate ($FOSS_CONFIG)" >&2
    exit 1
fi
echo "OK: foss release keeps -dontobfuscate"

if grep -qx -- "-dontobfuscate" "$GPLAY_CONFIG"; then
    echo "FAIL: gplay release still has -dontobfuscate ($GPLAY_CONFIG)" >&2
    exit 1
fi
echo "OK: gplay release has no -dontobfuscate"

RENAMED=$(awk -F' -> ' '/^eu\.darken\.bluemusic\..* -> / && $0 !~ /R8\$\$REMOVED/ { if ($2 != ($1 ":")) c++ } END { print c+0 }' "$GPLAY_MAPPING")
echo "Renamed app classes in gplayRelease: $RENAMED"
if [ "$RENAMED" -lt 500 ]; then
    echo "FAIL: only $RENAMED app classes were renamed, expected at least 500" >&2
    exit 1
fi
echo "OK: gplay release is obfuscated"

# Resolved by name at runtime (reflection, manifest/library keep rules, WorkManager's
# persisted class name), so a rename here is a runtime failure, not a cosmetic one.
KEEP_TARGETS="
eu.darken.bluemusic.BuildConfig
eu.darken.bluemusic.common.BuildConfigWrap
eu.darken.bluemusic.main.ui.widget.WidgetProvider
eu.darken.bluemusic.main.ui.widget.VolumeLockToggleAction
eu.darken.bluemusic.main.backup.core.BackupError\$MalformedBackup
eu.darken.bluemusic.main.backup.core.BackupError\$MissingBackupJson
eu.darken.bluemusic.upgrade.core.billing.GplayServiceUnavailableException
eu.darken.bluemusic.upgrade.core.billing.work.PurchaseAckWorker
"

for cls in $KEEP_TARGETS; do
    if ! grep -qF -- "$cls -> $cls:" "$GPLAY_MAPPING"; then
        echo "FAIL: $cls is not identity-mapped in $GPLAY_MAPPING" >&2
        exit 1
    fi
done
echo "OK: all keep targets are identity-mapped"

echo "check-r8-mapping: PASS"
