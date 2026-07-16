#!/usr/bin/env bash
#
# FreeFCC Edge — static forbidden-feature scan.
#
# Fails (exit 1) if any stripped feature reappears in the product tree: source,
# manifest, Gradle build files, resources, and the dependency-verification
# metadata. It does NOT scan the docs (FORK_NOTES.md / THIRD_PARTY_NOTICES),
# which name these tokens precisely to record that they are excluded.
#
# This is a safety net AFTER the build, not a substitute for the from-scratch
# minimal source tree. Run in CI and before every code handoff.
set -euo pipefail

cd "$(dirname "$0")/.."

# One regex, case-insensitive. Covers accessibility/automation/root, secure
# settings, screenshot/camera/flashlight, split-screen, notification listening,
# all-packages visibility, Glide/icon-pack, and the named upstream classes.
PATTERN='CAMERA|FLASHLIGHT|MediaProjection|SCREENSHOT|Shizuku|su -c|input keyevent|WRITE_SECURE_SETTINGS|toggle-split-screen|NotificationListenerService|BIND_ACCESSIBILITY_SERVICE|QUERY_ALL_PACKAGES|Glide|AppIconRequest|PanelAccessibilityService|AutomationManager|ActionDispatcher|HiddenApiBypass|freeform|POWER_MENU|one_handed'

# Product surfaces only.
TARGETS=(
  "app/src"
  "app/build.gradle.kts"
  "build.gradle.kts"
  "settings.gradle.kts"
  "gradle.properties"
  "gradle/verification-metadata.xml"
)

if grep -RInE --binary-files=without-match "$PATTERN" "${TARGETS[@]}"; then
  echo ""
  echo "FORBIDDEN-SCAN: FAIL — a stripped feature reappeared above."
  exit 1
fi

echo "FORBIDDEN-SCAN: PASS — 0 hits across source, manifest, Gradle and resources."
