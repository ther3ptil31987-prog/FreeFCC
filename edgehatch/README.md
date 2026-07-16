# EdgeHatch

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

Run everything from the project root:

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
stripped capability (automation/root, secure-settings, screenshot, camera,
split-screen, notification listening, all-packages visibility, Glide, etc.)
reappears in source, manifest, Gradle or resources, **and** enforces that the one
accessibility service (below) stays limited to overlay hosting — the permission
only in the manifest, the accessibility overlay-window type only in that service,
and screen-content / events / nodes / gesture-dispatch / global-actions all at
zero.

## Permissions

Declared, and only these:

- `SYSTEM_ALERT_WINDOW` — draw the edge handle / panel over other apps (overlay mode).
- `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` — keep the overlay alive.
- `RECEIVE_BOOT_COMPLETED` — re-arm the overlay after reboot (opt-in; overlay mode only).
- `BIND_ACCESSIBILITY_SERVICE` (on the optional service) — see **Accessibility mode** below.

App visibility uses a scoped `<queries>` MAIN/LAUNCHER element plus the explicit
DJI Fly packages — **not** `QUERY_ALL_PACKAGES`. There is no automation/root/ADB,
no notification listener, and no network access.

### Accessibility mode (RC 2 compatibility)

On locked devices such as the DJI RC 2, `SYSTEM_ALERT_WINDOW` cannot be granted.
For those, EdgeHatch ships an **optional** accessibility service — a
**high-privilege compatibility mode, not a disability aid** — that hosts the same
edge handle in an accessibility overlay window, without the overlay permission. It
is deliberately minimal: `isAccessibilityTool=false`, `canRetrieveWindowContent=false`,
`onAccessibilityEvent` is a no-op, and it uses no node, event, gesture,
global-action or window-inspection API (enforced by `forbidden-scan.sh`). Only one
drawer is ever active — the accessibility path takes priority over the
foreground-service overlay so the handle is never drawn twice. Reboot behaviour
differs by mode: in accessibility mode the handle returns automatically while the
service stays enabled; in overlay mode it returns only with "Start after reboot".

## Install on a locked device (DJI RC 2)

`SYSTEM_ALERT_WINDOW`, changing the default launcher, and enabling accessibility
services are all restricted for side-loaded apps on the RC 2. Enabling the
accessibility service requires the app to be installed by an installer that
attributes it to a trusted source (so it is not treated as "unknown source").

**How to obtain such an installer is out of scope for this project, and
EdgeHatch neither ships nor endorses one.** Use only an installer you already
trust — for example one that ships with the device, or a package installer you
build yourself from audited source. Do **not** side-load an unverified or
unknown-provenance installer APK.

> Provenance note (honest): our own RC 2 bring-up used a redistributed
> `com.android.packageinstaller` APK whose certificate carries only a
> self-signed "DJI" free-text DN of **unproven origin**, and whose package name
> can collide with the device's system installer. That was a **provenance-open
> test path**, adequate to prove EdgeHatch's accessibility overlay works — it is
> **not** a recommendation to install or distribute that APK.

Once EdgeHatch is installed by a trusted installer:

1. Open **Accessibility mode** → enable the EdgeHatch service under Accessibility,
   then turn on **Edge handle**. (If the overlay permission *can* be granted on
   your device, use that instead — the accessibility service is not needed there.)
2. Select the apps for the panel (e.g. DJI Fly, a file manager) and drive over
   DJI Fly. Validate with `RC2_GROUND_TEST.md` — ground only, no propellers.

EdgeHatch does **not** need to be the default launcher; it overlays whatever is in
the foreground (DJI Fly), so it works even where the home role cannot be changed.

## Signing & release

EdgeHatch is signed locally with a dedicated, **git-ignored** keystore. No
keystore or signing secret is ever committed. Without a configured keystore,
`assembleRelease` produces an unsigned APK.

**1. Create a keystore once** (keep it private; never commit the `.jks`):

```bash
keytool -genkeypair -v \
  -keystore edgehatch-release.jks -alias edgehatch \
  -keyalg RSA -keysize 4096 -validity 10000
```

**2. Point the build at it** — copy `keystore.properties.example` to
`edgehatch/keystore.properties` (git-ignored) and fill in the path/passwords, or
export `EDGEHATCH_STORE_FILE` / `EDGEHATCH_STORE_PASSWORD` / `EDGEHATCH_KEY_ALIAS`
/ `EDGEHATCH_KEY_PASSWORD`. The Gradle build picks these up automatically and
signs the release with the v1+v2 schemes.

**3. Build and verify:**

```bash
./gradlew assembleRelease
# Verify the signature and print the signer certificate + its SHA-256:
apksigner verify --verbose --print-certs \
  app/build/outputs/apk/release/app-release.apk
# Record the APK's own SHA-256 for distribution:
shasum -a 256 app/build/outputs/apk/release/app-release.apk
```

Publish the signer certificate's SHA-256 fingerprint so users can confirm every
future update is signed by the same key.

## Delivery pack

`./scripts/build-release-pack.sh` assembles a **signed** pack under
`build/edgehatch-pack/` (git-ignored): the release APK, `README.md`,
`FORK_NOTES.md`, `THIRD_PARTY_NOTICES`, `SOURCE_COMMIT.txt`, and
`SHA256SUMS.txt`.

The script is **fail-closed** — it aborts without writing a pack if any of these
is not met: the versioned `edgehatch/` tree is clean vs `HEAD` (so the recorded
commit id matches the build), `apksigner` is resolvable, a release keystore is
configured, and the built APK is signed and passes `apksigner verify`.

An **unsigned** `./gradlew assembleRelease` build is only a local / RC2 test
build — it is never packaged and never distributed. The pack contains only a
signed APK reproducibly built from this tree; no third-party-signed, Play-Store,
or otherwise unverified APKs.
