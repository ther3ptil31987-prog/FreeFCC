# EdgeHatch (`edgehatch/`)

EdgeHatch is an independent, self-contained Android edge launcher
(`app.edgehatch.launcher`). It draws a slim handle at the screen edge; tapping or
swiping the handle opens a short, user-chosen list of apps over whatever app is
in the foreground and launches the selected one.

It is a standalone Gradle project. It is compatible with — but not affiliated
with or endorsed by — DJI; the DJI RC 2 controller and the DJI Fly app are named
here only as a compatibility reference, and "DJI" is a trademark of its owner.
EdgeHatch makes no claim of partnership with DJI or with any other project.

See `FORK_NOTES.md` for how it derives from its pinned MIT reference (Smart-Edge)
and `THIRD_PARTY_NOTICES` for licenses.

## Requirements

- JDK 17
- Android SDK (set `sdk.dir` in `edgehatch/local.properties`, or `ANDROID_HOME`)
- The wrapper pins Gradle 8.10.2 by SHA-256; AGP 8.7.0, Kotlin 2.0.21,
  `compileSdk`/`targetSdk` 35, `minSdk` 29.

## Build, test, verify (reproducible)

Run everything from `edgehatch/`:

```bash
# Unit tests (pure JVM: gesture, boot gate, clamping, snapshot ordering)
./gradlew testDebugUnitTest

# Debug + release APKs (release is unsigned unless a keystore is configured)
./gradlew assembleDebug assembleRelease

# Strict dependency verification against a throwaway Gradle home. Fails if any
# resolved artifact does not match gradle/verification-metadata.xml.
./gradlew -g "$(mktemp -d)" testDebugUnitTest assembleDebug assembleRelease

# Static forbidden-feature scan (0 hits required)
./scripts/forbidden-scan.sh
```

`gradle/verification-metadata.xml` pins every resolved dependency by SHA-256
(no MD5/SHA-1, no trust exceptions). `scripts/forbidden-scan.sh` fails if any
stripped capability (accessibility, automation/root, secure-settings, screenshot,
camera, split-screen, notification listening, all-packages visibility, Glide,
etc.) reappears in source, manifest, Gradle or resources.

## Permissions

Declared, and only these:

- `SYSTEM_ALERT_WINDOW` — draw the edge handle / panel over other apps.
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` — keep the overlay alive.
- `RECEIVE_BOOT_COMPLETED` — re-arm the overlay after reboot (opt-in only).

App visibility uses a scoped `<queries>` MAIN/LAUNCHER element plus the explicit
DJI Fly packages — **not** `QUERY_ALL_PACKAGES`. There is no accessibility
service, no automation/root/ADB, no notification listener, and no network access.

## Signing boundary

The helper is signed locally with a dedicated, **git-ignored** keystore. No real
keystore or signing secret is committed or used in a fork/PR context. Without a
configured keystore, `assembleRelease` produces an unsigned APK.

## Delivery pack contents

The shipped helper pack contains only:

- the APK(s) reproducibly built from this tree,
- `README.md`, `FORK_NOTES.md`, `THIRD_PARTY_NOTICES`,
- the source commit id it was built from,
- each APK's SHA-256.

No third-party-signed, Play-Store, or otherwise unverified APKs are included.
