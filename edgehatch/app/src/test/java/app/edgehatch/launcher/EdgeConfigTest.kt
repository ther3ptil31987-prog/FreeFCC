package app.edgehatch.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure config guards: value clamping to safe ranges, the
 * "at least one trigger" rule, and the snapshot version ordering that prevents a
 * stale apply from rolling back a newer value.
 */
class EdgeConfigTest {

    @Test
    fun widthClampsToRange() {
        assertEquals(EdgeLimits.WIDTH_MIN, EdgeLimits.clampWidthDp(-5))
        assertEquals(EdgeLimits.WIDTH_MAX, EdgeLimits.clampWidthDp(999))
        assertEquals(20, EdgeLimits.clampWidthDp(20))
    }

    @Test
    fun heightClampsToRange() {
        assertEquals(EdgeLimits.HEIGHT_MIN, EdgeLimits.clampHeightDp(0))
        assertEquals(EdgeLimits.HEIGHT_MAX, EdgeLimits.clampHeightDp(10_000))
        assertEquals(120, EdgeLimits.clampHeightDp(120))
    }

    @Test
    fun opacityClampsAndHandlesNaN() {
        assertEquals(EdgeLimits.OPACITY_MIN, EdgeLimits.clampOpacity(0f), 0.0001f)
        assertEquals(EdgeLimits.OPACITY_MAX, EdgeLimits.clampOpacity(5f), 0.0001f)
        assertEquals(EdgeLimits.OPACITY_DEFAULT, EdgeLimits.clampOpacity(Float.NaN), 0.0001f)
    }

    @Test
    fun biasClampsAndHandlesNaN() {
        assertEquals(0f, EdgeLimits.clampBias(-1f), 0.0001f)
        assertEquals(1f, EdgeLimits.clampBias(2f), 0.0001f)
        assertEquals(EdgeLimits.BIAS_DEFAULT, EdgeLimits.clampBias(Float.NaN), 0.0001f)
    }

    @Test
    fun safeTriggersKeepsAtLeastOneOn() {
        assertEquals(true to false, EdgeLimits.safeTriggers(tap = false, swipe = false))
        assertEquals(false to true, EdgeLimits.safeTriggers(tap = false, swipe = true))
        assertEquals(true to true, EdgeLimits.safeTriggers(tap = true, swipe = true))
    }

    @Test
    fun newerSnapshotApplies() {
        assertTrue(shouldApplySnapshot(incomingVersion = 5, appliedVersion = 4))
        assertTrue(shouldApplySnapshot(incomingVersion = 0, appliedVersion = -1))
    }

    @Test
    fun staleOrEqualSnapshotIsRejected() {
        assertFalse(shouldApplySnapshot(incomingVersion = 4, appliedVersion = 4))
        assertFalse(shouldApplySnapshot(incomingVersion = 3, appliedVersion = 7))
    }
}
