package app.edgehatch.launcher

/**
 * Which mechanism draws the edge handle. Extracted as a pure decision so the
 * dual-path (accessibility vs foreground-service overlay) coordination is
 * unit-testable and deterministic — never a double handle, never a silent draw.
 */
enum class DrawPath {
    /** Not drawn: helper disabled, or no permitted mechanism available. */
    NONE,

    /** Drawn by the accessibility service in an accessibility overlay window. */
    ACCESSIBILITY,

    /** Drawn by the foreground service via TYPE_APPLICATION_OVERLAY. */
    OVERLAY,
}

/**
 * The single source of truth for which path draws.
 *
 * Rules:
 *  - disabled            → NONE
 *  - accessibility on    → ACCESSIBILITY (it takes priority; the FGS yields so
 *                          the handle is never drawn twice)
 *  - else overlay grant  → OVERLAY
 *  - neither             → NONE (permission revoked / nothing granted)
 *
 * `accessibilityConnected` wins over `canDrawOverlays` on purpose: on locked
 * devices (RC 2) the accessibility path is the one that actually works, and
 * keeping a single active drawer avoids a duplicated handle when both are
 * available.
 */
fun chooseDrawPath(
    enabled: Boolean,
    accessibilityConnected: Boolean,
    canDrawOverlays: Boolean,
): DrawPath = when {
    !enabled -> DrawPath.NONE
    accessibilityConnected -> DrawPath.ACCESSIBILITY
    canDrawOverlays -> DrawPath.OVERLAY
    else -> DrawPath.NONE
}
