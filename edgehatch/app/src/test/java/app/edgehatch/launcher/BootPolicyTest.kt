package app.edgehatch.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the pure boot-gate used by BootReceiver: the overlay is re-armed only
 * when enabled AND opted into boot start AND the overlay permission is held.
 * Any missing condition must fail closed (no start).
 */
class BootPolicyTest {

    @Test
    fun startsOnlyWhenAllConditionsHold() {
        assertTrue(shouldStartOnBoot(enabled = true, autoStart = true, canDrawOverlays = true))
    }

    @Test
    fun doesNotStartWhenDisabled() {
        assertFalse(shouldStartOnBoot(enabled = false, autoStart = true, canDrawOverlays = true))
    }

    @Test
    fun doesNotStartWhenAutoStartOff() {
        assertFalse(shouldStartOnBoot(enabled = true, autoStart = false, canDrawOverlays = true))
    }

    @Test
    fun doesNotStartWhenOverlayRevoked() {
        // The key fail-safe: overlay permission revoked → no start, even if
        // enabled and auto-start were both on before the reboot.
        assertFalse(shouldStartOnBoot(enabled = true, autoStart = true, canDrawOverlays = false))
    }
}
