package com.freefcc.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Covers HardwareLock's mutual exclusion — the guarantee S-006 relies on to stop
 * FccKeepaliveService writing DUMPL frames while FccViewModel holds the lock (or
 * vice versa). The held lock is only released after the concurrent tryBegin()
 * attempt has actually run, via CountDownLatch, so "exactly one wins" can't pass
 * by accident from a lock that was already free by the time the second call ran.
 */
class HardwareLockTest {

    @Test
    fun secondTryBeginFailsWhileFirstHoldsTheLock() {
        assertTrue("lock must be free at test start", HardwareLock.tryBegin())
        assertTrue(HardwareLock.busy.value)

        val secondAttempted = CountDownLatch(1)
        val secondResult = AtomicBoolean(true)

        val secondThread = Thread {
            secondResult.set(HardwareLock.tryBegin())
            secondAttempted.countDown()
        }
        secondThread.start()

        assertTrue(
            "second thread's tryBegin() never ran",
            secondAttempted.await(5, TimeUnit.SECONDS)
        )
        secondThread.join(5000)

        assertFalse("second tryBegin() must fail while the first op still holds the lock", secondResult.get())

        HardwareLock.end()
        assertFalse(HardwareLock.busy.value)
        assertTrue("lock must be free again after end()", HardwareLock.tryBegin())
        HardwareLock.end()
    }
}
