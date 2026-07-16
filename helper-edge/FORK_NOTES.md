# FreeFCC Edge — fork / derivation notes

`helper-edge/` is a small, self-contained Android app (`com.freefcc.edge`) that
gives the DJI RC 2 controller a slim edge handle to open a short list of apps
over DJI Fly. It replaces two problematic bundled helpers — `03_ATVLauncher`
(a re-signed paid launcher, see S-009) and `04_Edge Gestures` — with a single
MIT-clean build.

## Reference base (pinned)

- **Project:** Smart-Edge — <https://github.com/Imtiaz-Official/Smart-Edge>
- **Commit:** `c54beef6ae1a77ab8499df6c6a2a55b629da143b` (tag `v1.3.6`)
- **License:** MIT © 2024 Imtiaz (full text in `THIRD_PARTY_NOTICES`)

Smart-Edge is used as a **documented reference**, not vendored wholesale. None of
its 40 source files are copied in. The nine components here were written from
scratch for the narrow "edge handle → app panel → launch" path.

## What was adapted (with attribution)

Only the WindowManager overlay technique was adapted, and it is attributed
inline where used:

- `EdgeOverlayService.addHandle()` / `handleParams()` — the overlay parameters
  (`TYPE_APPLICATION_OVERLAY` plus the `FLAG_NOT_FOCUSABLE | FLAG_NOT_TOUCH_MODAL |
  FLAG_LAYOUT_IN_SCREEN` combination for a pass-through edge handle) are adapted
  from Smart-Edge `FloatingPanelService.addEdgeHandle` at the pinned commit. See
  the class-level comment in `EdgeOverlayService.kt`.

## What was deliberately NOT carried over

The reference project also ships an AccessibilityService, a Shizuku/root
automation engine, secure-settings granting, split-screen, notification
listening, a quick-settings tile, screenshot/flashlight/camera actions, icon
packs and a Glide image pipeline. **None of these exist here** — not their code,
permissions, dependencies, or resources. The static forbidden-feature scan (see
below) enforces their absence.

## The nine components

`EdgeOverlayService`, `EdgeHandleView`, `EdgePanelView`, `LauncherAppRepository`,
`AppEntry`, `EdgePreferences`, `MainActivity`, `BootReceiver`, `SidePanelApp`.

Icons come straight from `PackageManager` / launcher activities (no Glide). The
app list uses a scoped `<queries>` MAIN/LAUNCHER element plus the explicit DJI
Fly packages — no `QUERY_ALL_PACKAGES`.

## Live configuration (no service restart)

`MainActivity` persists each validated value atomically into `EdgePreferences`
(which clamps it and bumps a monotonic `configVersion`), then sends an explicit,
non-exported `ACTION_APPLY_CONFIGURATION` to `EdgeOverlayService`. The service
loads a full `EdgeConfig` snapshot and reconciles the resting handle with
`WindowManager.updateViewLayout`; only a side change removes and re-adds its own
handle. An open panel refreshes its app list in place. A newer snapshot always
wins (`shouldApplySnapshot`), so a stale/queued apply can never roll back a newer
value, and slider changes are debounced. Live parameters: enable, side, vertical
position, width, height, opacity, tap/swipe triggers, selected apps. `autoStart`
is stored only and applies at the next boot. The in-app disable switch and the
notification Stop action both remove every overlay view immediately. No parameter
is settable via accessibility, ADB, network or remote control.

## Toolchain

Reuses the main FreeFCC project's verified toolchain: Gradle 8.10.2 (wrapper
`distributionSha256Sum` `31c55713…`, independently verified in S-011), AGP
8.7.0, Kotlin 2.0.21, Java 17, `compileSdk`/`targetSdk` 35, `minSdk` 29. No SDK
downgrade. Dependencies are AndroidX + Material only — no JitPack, Glide,
Shizuku, hidden-api-bypass or color-picker. `gradle/verification-metadata.xml`
pins every resolved artifact by SHA-256.

## Forbidden-feature scan

A static scan (`0` hits required) covers source, manifest, Gradle and resources
for: `CAMERA`, `FLASHLIGHT`, `MediaProjection`, `SCREENSHOT`, `Shizuku`,
`su -c`, `input keyevent`, `WRITE_SECURE_SETTINGS`, `toggle-split-screen`,
`NotificationListenerService`, `BIND_ACCESSIBILITY_SERVICE`,
`QUERY_ALL_PACKAGES`, `Glide`, `AppIconRequest`, `PanelAccessibilityService`,
`AutomationManager`, `ActionDispatcher`, `HiddenApiBypass`, `freeform`,
`POWER_MENU`, `one_handed`.

## Signing & delivery

The helper is signed locally with a dedicated, git-ignored keystore (never a
real keystore in a fork/PR context). The shipped helper pack contains only APKs
reproducibly built from this tree, plus this file, `THIRD_PARTY_NOTICES`, the
build instructions, the source commit id, and each APK's SHA-256.

## `_m3` layout note

The reference project keeps duplicate `_m3` (Material 3) layout variants. This
fork does not: it ships a single Material 3 layout set, so no `_m3` duplicates
exist here.
