#!/usr/bin/env bash
#
# Proves forbidden-scan.sh catches the accessibility negative cases:
#   - reading the AccessibilityEvent parameter (event?.…)
#   - a second AccessibilityService subclass
#   - a missing accessibility service
# Each must make the scan FAIL. A clean copy must PASS.
set -uo pipefail

cd "$(dirname "$0")/.."
SCAN="$(pwd)/scripts/forbidden-scan.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

pass=0
fail=0

run() { # desc  expect(pass|fail)
  local desc="$1" expect="$2" got
  if SCAN_ROOT="$TMP" bash "$SCAN" >/dev/null 2>&1; then got=pass; else got=fail; fi
  if [ "$got" = "$expect" ]; then
    echo "  ok: $desc (scan=$got)"
    pass=$((pass + 1))
  else
    echo "  MISMATCH: $desc (scan=$got, expected=$expect)"
    fail=$((fail + 1))
  fi
}

seed() { # fresh clean copy of the scanned source into $TMP
  rm -rf "$TMP/app"
  mkdir -p "$TMP/app"
  cp -R app/src "$TMP/app/src"
}

SVC="app/src/main/java/app/edgehatch/launcher/EdgeAccessibilityService.kt"

# Baseline: a clean copy passes.
seed
run "clean tree passes" pass

# Negative 1: any read of the AccessibilityEvent parameter is rejected.
seed
printf '\n// tamper: event?.packageName\n' >> "$TMP/$SVC"
run "event-parameter read is rejected" fail

# Negative 2: a second AccessibilityService subclass is rejected.
seed
cat > "$TMP/app/src/main/java/app/edgehatch/launcher/SecondA11y.kt" <<'EOF'
package app.edgehatch.launcher
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
class SecondA11y : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
EOF
run "a second accessibility service is rejected" fail

# Negative 3: a missing accessibility service is rejected.
seed
rm -f "$TMP/$SVC"
run "a missing accessibility service is rejected" fail

echo "SELFTEST: $pass ok, $fail mismatched."
[ "$fail" -eq 0 ]
