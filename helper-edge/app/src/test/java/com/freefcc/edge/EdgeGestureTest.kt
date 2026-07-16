package com.freefcc.edge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure edge-trigger decision: a tap or a short inward swipe opens the
 * panel, gated by which triggers are enabled. Outward or parallel movement never
 * opens, and with both triggers off nothing opens. slop = 16f throughout.
 */
class EdgeGestureTest {

    private val slop = 16f

    @Test
    fun tapOpensWhenTapEnabled() {
        assertTrue(isEdgeTrigger(2f, 3f, slop, onLeft = false, tapEnabled = true, swipeEnabled = true))
        assertTrue(isEdgeTrigger(2f, 3f, slop, onLeft = true, tapEnabled = true, swipeEnabled = false))
    }

    @Test
    fun tapDoesNotOpenWhenTapDisabled() {
        assertFalse(isEdgeTrigger(2f, 3f, slop, onLeft = false, tapEnabled = false, swipeEnabled = true))
    }

    @Test
    fun inwardSwipeOpensWhenSwipeEnabled() {
        // Right edge: inward = negative dx. Left edge: inward = positive dx.
        // Both are horizontally dominant (abs(dx) > abs(dy)).
        assertTrue(isEdgeTrigger(-40f, 5f, slop, onLeft = false, tapEnabled = false, swipeEnabled = true))
        assertTrue(isEdgeTrigger(40f, 5f, slop, onLeft = true, tapEnabled = false, swipeEnabled = true))
    }

    @Test
    fun verticalDominantInwardDriftDoesNotOpen() {
        // Long vertical drag with only a slight inward dx must NOT open (the
        // handle sits over DJI Fly). Right edge: dx=-17, dy=120.
        assertFalse(isEdgeTrigger(-17f, 120f, slop, onLeft = false, tapEnabled = true, swipeEnabled = true))
        // Left edge mirror: dx=17, dy=120.
        assertFalse(isEdgeTrigger(17f, 120f, slop, onLeft = true, tapEnabled = true, swipeEnabled = true))
    }

    @Test
    fun inwardSwipeDoesNotOpenWhenSwipeDisabled() {
        assertFalse(isEdgeTrigger(-40f, 5f, slop, onLeft = false, tapEnabled = true, swipeEnabled = false))
    }

    @Test
    fun outwardSwipeDoesNotOpen() {
        assertFalse(isEdgeTrigger(40f, 5f, slop, onLeft = false, tapEnabled = true, swipeEnabled = true))
        assertFalse(isEdgeTrigger(-40f, 5f, slop, onLeft = true, tapEnabled = true, swipeEnabled = true))
    }

    @Test
    fun longVerticalDragDoesNotOpen() {
        assertFalse(isEdgeTrigger(3f, 120f, slop, onLeft = false, tapEnabled = true, swipeEnabled = true))
    }

    @Test
    fun bothTriggersOffNeverOpens() {
        assertFalse(isEdgeTrigger(2f, 3f, slop, onLeft = false, tapEnabled = false, swipeEnabled = false))
        assertFalse(isEdgeTrigger(-40f, 5f, slop, onLeft = false, tapEnabled = false, swipeEnabled = false))
    }
}
