#!/usr/bin/env bash
#
# EdgeHatch — static forbidden-feature scan.
#
# Fails (exit 1) if any stripped feature reappears in the product tree: source,
# manifest, Gradle build files, resources, and the dependency-verification
# metadata. It does NOT scan the docs (README.md / FORK_NOTES.md /
# THIRD_PARTY_NOTICES), which name these tokens precisely to record that they
# are excluded.
#
# A narrow, explicit exception exists for the RC 2 overlay-host accessibility
# service (S-014): the `BIND_ACCESSIBILITY_SERVICE` permission is allowed ONLY in
# the manifest, and the accessibility overlay-window type ONLY in the one
# overlay-host service. Everything else about accessibility — screen content,
# EVENT reading (event fields/constants), nodes, gesture dispatch, global
# actions, window inspection, extra services — must still scan to zero, and
# EXACTLY one accessibility service must be declared exactly as expected.
#
# Self-testable: set SCAN_ROOT to run the same checks against another tree copy
# (used by scripts/forbidden-scan-selftest.sh to prove the negative cases).
#
# This is a safety net AFTER the build, not a substitute for the from-scratch
# minimal source tree. Run in CI and before every code handoff.
set -euo pipefail

ROOT="${SCAN_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
cd "$ROOT"

# Product surfaces only.
TARGETS=(
  "app/src"
  "app/build.gradle.kts"
  "build.gradle.kts"
  "settings.gradle.kts"
  "gradle.properties"
  "gradle/verification-metadata.xml"
)
# Keep only targets that exist (a self-test copy may omit some).
EXISTING=()
for t in "${TARGETS[@]}"; do [ -e "$t" ] && EXISTING+=("$t"); done

fail=0

# 1) Hard-forbidden features — 0 hits anywhere in product surfaces.
FORBIDDEN='CAMERA|FLASHLIGHT|MediaProjection|SCREENSHOT|Shizuku|su -c|input keyevent|WRITE_SECURE_SETTINGS|toggle-split-screen|NotificationListenerService|QUERY_ALL_PACKAGES|Glide|AppIconRequest|PanelAccessibilityService|AutomationManager|ActionDispatcher|HiddenApiBypass|freeform|POWER_MENU|one_handed'
if grep -RInE --binary-files=without-match "$FORBIDDEN" "${EXISTING[@]}"; then
  echo "FORBIDDEN-SCAN: FAIL — a stripped feature reappeared above."
  fail=1
fi

# 2) Accessibility ABUSE surface — must stay 0 even though a narrow overlay-only
#    accessibility service is allowed. Screen content, nodes, EVENT reading
#    (the AccessibilityEvent parameter is dereferenced, or its TYPE_ constants
#    are used), gesture dispatch, global actions, window inspection, touch
#    exploration / key filtering, event-type declarations, and a "true"
#    content-retrieval flag.  onAccessibilityEvent must stay a no-op: the only
#    nullable `event?.` in this codebase is that parameter, so any `event?.`
#    read is forbidden.
A11Y_ABUSE='getRootInActiveWindow|AccessibilityNodeInfo|findAccessibilityNodeInfos|dispatchGesture|GestureDescription|performGlobalAction|getWindows\(|AccessibilityWindowInfo|canRetrieveWindowContent="true"|flagRequestTouchExploration|flagRequestFilterKeyEvents|flagReportViewIds|accessibilityEventTypes|AccessibilityEvent\.TYPE_|event\?\.'
if grep -RInE --binary-files=without-match "$A11Y_ABUSE" "${EXISTING[@]}"; then
  echo "FORBIDDEN-SCAN: FAIL — accessibility abuse / event-read surface reappeared above."
  fail=1
fi

# 3) Narrow allowlist: BIND_ACCESSIBILITY_SERVICE only in the manifest;
#    the accessibility overlay-window type only in the one overlay-host service.
while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    */AndroidManifest.xml) : ;;
    *) echo "FORBIDDEN-SCAN: FAIL — BIND_ACCESSIBILITY_SERVICE outside the manifest: $f"; fail=1 ;;
  esac
done < <(grep -RIlE --binary-files=without-match 'BIND_ACCESSIBILITY_SERVICE' "${EXISTING[@]}" || true)

while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    */EdgeAccessibilityService.kt) : ;;
    *) echo "FORBIDDEN-SCAN: FAIL — TYPE_ACCESSIBILITY_OVERLAY outside the overlay host: $f"; fail=1 ;;
  esac
done < <(grep -RIlE --binary-files=without-match 'TYPE_ACCESSIBILITY_OVERLAY' "${EXISTING[@]}" || true)

# 4) EXACTLY one AccessibilityService subclass, and exactly the expected manifest
#    declaration (name + BIND permission + the accessibility-service action).
if [ -d app/src ]; then
  svc_count="$(grep -RIlE --binary-files=without-match ':[[:space:]]*AccessibilityService\(\)' app/src 2>/dev/null | wc -l | tr -d ' ')"
  if [ "${svc_count:-0}" -ne 1 ]; then
    echo "FORBIDDEN-SCAN: FAIL — expected exactly one AccessibilityService subclass, found ${svc_count}."
    fail=1
  fi
fi

MANIFEST="app/src/main/AndroidManifest.xml"
if [ -f "$MANIFEST" ]; then
  action_count="$(grep -cE 'android\.accessibilityservice\.AccessibilityService' "$MANIFEST" || true)"
  if [ "${action_count:-0}" -ne 1 ] \
     || ! grep -q '\.EdgeAccessibilityService' "$MANIFEST" \
     || ! grep -q 'android.permission.BIND_ACCESSIBILITY_SERVICE' "$MANIFEST"; then
    echo "FORBIDDEN-SCAN: FAIL — the manifest must declare exactly one EdgeAccessibilityService guarded by BIND_ACCESSIBILITY_SERVICE."
    fail=1
  fi
fi

if [ "$fail" -ne 0 ]; then
  exit 1
fi

echo "FORBIDDEN-SCAN: PASS — 0 forbidden hits; accessibility limited to overlay hosting."
