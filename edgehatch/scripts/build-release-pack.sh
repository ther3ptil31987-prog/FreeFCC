#!/usr/bin/env bash
#
# Build the EdgeHatch delivery pack: a SIGNED release APK plus its licenses,
# source commit id, and SHA-256.
#
# Fail-closed. A pack is produced ONLY when all of the following hold; any
# failure aborts without writing a pack:
#   1. the versioned edgehatch/ tree is clean vs HEAD (no staged/unstaged/
#      untracked product files), so the recorded commit id matches the build;
#   2. apksigner is resolvable;
#   3. a release keystore is configured;
#   4. the built APK is signed and `apksigner verify` succeeds.
#
# An UNSIGNED build is never packaged. For a local / RC2 test build run
# `./gradlew assembleRelease` directly — that produces an unsigned APK, which is
# fine for on-device testing but must never be distributed.
#
# Nothing here creates or embeds a keystore; signing comes from
# edgehatch/keystore.properties (git-ignored) or the EDGEHATCH_* env vars.
#
# Usage:  ./scripts/build-release-pack.sh [output_dir]
set -euo pipefail

cd "$(dirname "$0")/.."
# Default output under build/ (git-ignored) so a pack is never committed.
OUT="${1:-build/edgehatch-pack}"

# --- Gate 1: clean, committed edgehatch/ tree ---------------------------------
# Scoped to the current directory (edgehatch/), so an unrelated local root
# .gitignore change does not affect this check. Ignored files (build/,
# local.properties, keystore.properties, *.jks) are not reported by porcelain.
DIRTY="$(git status --porcelain -- .)"
if [ -n "$DIRTY" ]; then
  echo "ERROR: edgehatch/ has uncommitted changes; commit or stash before packing." >&2
  echo "$DIRTY" >&2
  exit 1
fi
COMMIT="$(git rev-parse HEAD)"

# --- Gate 2: resolve apksigner (portable) -------------------------------------
resolve_apksigner() {
  if command -v apksigner >/dev/null 2>&1; then
    command -v apksigner
    return 0
  fi
  local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ] && [ -f local.properties ]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | head -1)"
  fi
  if [ -z "$sdk" ]; then
    case "$(uname -s)" in
      Darwin) sdk="$HOME/Library/Android/sdk" ;;
      *) sdk="$HOME/Android/Sdk" ;;
    esac
  fi
  # Highest build-tools version, sorted numerically by dotted components
  # (portable; avoids GNU-only `sort -V`).
  local bt
  bt="$(ls -1 "$sdk/build-tools" 2>/dev/null | sort -t. -k1,1n -k2,2n -k3,3n | tail -1)"
  if [ -n "$bt" ] && [ -x "$sdk/build-tools/$bt/apksigner" ]; then
    echo "$sdk/build-tools/$bt/apksigner"
    return 0
  fi
  return 1
}
APKSIGNER="$(resolve_apksigner || true)"
if [ -z "$APKSIGNER" ]; then
  echo "ERROR: apksigner not found via PATH, ANDROID_HOME/ANDROID_SDK_ROOT, or local.properties (sdk.dir)." >&2
  echo "Install the Android build-tools; a pack cannot be verified without it." >&2
  exit 1
fi

# --- Gate 3: keystore configured ----------------------------------------------
if [ ! -f keystore.properties ] && [ -z "${EDGEHATCH_STORE_FILE:-}" ]; then
  echo "ERROR: no release keystore configured (edgehatch/keystore.properties or EDGEHATCH_* env)." >&2
  echo "See README 'Signing & release'. Unsigned builds are never packaged." >&2
  exit 1
fi

echo "==> Assembling signed release (commit $COMMIT)"
./gradlew assembleRelease --no-daemon

# --- Gate 4: signed APK present and verifies ----------------------------------
APK="app/build/outputs/apk/release/app-release.apk"
if [ ! -f "$APK" ]; then
  echo "ERROR: signed release APK not found ($APK)." >&2
  echo "An unsigned APK here means signing did not take effect; check the keystore." >&2
  ls -1 app/build/outputs/apk/release/ >&2 || true
  exit 1
fi

echo "==> Verifying signature"
"$APKSIGNER" verify --verbose --print-certs "$APK"

# --- Assemble the pack --------------------------------------------------------
mkdir -p "$OUT"
cp "$APK" "$OUT/"
cp README.md FORK_NOTES.md THIRD_PARTY_NOTICES "$OUT/"
echo "$COMMIT" > "$OUT/SOURCE_COMMIT.txt"
( cd "$OUT" && shasum -a 256 ./*.apk > SHA256SUMS.txt )

echo ""
echo "Signed delivery pack ready in: $OUT"
ls -1 "$OUT"
echo ""
cat "$OUT/SHA256SUMS.txt"
echo "source commit: $COMMIT"
