package com.freefcc.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers DumlTransport.allFramesSucceeded() — the aggregation rule Codex
 * required for S-008: a non-empty send series must report success only if
 * every single write succeeded; an empty series is always a failure.
 */
class DumlTransportSendFramesTest {

    @Test
    fun allWritesSucceeding_reportsSuccess() {
        assertTrue(DumlTransport.allFramesSucceeded(listOf(true, true, true)))
    }

    @Test
    fun oneFailedWriteAmongMany_reportsFailure() {
        assertFalse(DumlTransport.allFramesSucceeded(listOf(true, false, true)))
    }

    @Test
    fun emptySeries_reportsFailure() {
        assertFalse(DumlTransport.allFramesSucceeded(emptyList()))
    }
}
