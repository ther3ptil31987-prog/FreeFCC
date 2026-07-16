package app.edgehatch.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pure owner/lifecycle decisions for the dual-path overlay: which mechanism
 * draws the handle given (enabled, accessibility-connected, overlay-granted).
 * Guarantees a single active drawer and no draw when disabled or unpermitted.
 */
class DrawPolicyTest {

    @Test
    fun disabledNeverDraws() {
        assertEquals(DrawPath.NONE, chooseDrawPath(enabled = false, accessibilityConnected = false, canDrawOverlays = false))
        assertEquals(DrawPath.NONE, chooseDrawPath(enabled = false, accessibilityConnected = true, canDrawOverlays = true))
    }

    @Test
    fun accessibilityOnlyDrawsViaAccessibility() {
        assertEquals(
            DrawPath.ACCESSIBILITY,
            chooseDrawPath(enabled = true, accessibilityConnected = true, canDrawOverlays = false),
        )
    }

    @Test
    fun overlayOnlyDrawsViaOverlay() {
        assertEquals(
            DrawPath.OVERLAY,
            chooseDrawPath(enabled = true, accessibilityConnected = false, canDrawOverlays = true),
        )
    }

    @Test
    fun bothAvailableAccessibilityWinsSoNoDoubleHandle() {
        assertEquals(
            DrawPath.ACCESSIBILITY,
            chooseDrawPath(enabled = true, accessibilityConnected = true, canDrawOverlays = true),
        )
    }

    @Test
    fun revokedPermissionsStopDrawing() {
        // Enabled but neither mechanism available (e.g. overlay revoked and the
        // accessibility service disconnected).
        assertEquals(
            DrawPath.NONE,
            chooseDrawPath(enabled = true, accessibilityConnected = false, canDrawOverlays = false),
        )
    }

    @Test
    fun reconnectResumesAccessibilityPath() {
        // Handle after an accessibility disconnect (NONE) then reconnect.
        assertEquals(
            DrawPath.NONE,
            chooseDrawPath(enabled = true, accessibilityConnected = false, canDrawOverlays = false),
        )
        assertEquals(
            DrawPath.ACCESSIBILITY,
            chooseDrawPath(enabled = true, accessibilityConnected = true, canDrawOverlays = false),
        )
    }
}
