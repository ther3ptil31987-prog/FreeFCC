package com.freefcc.edge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Pure boot-gate decision, extracted for unit testing: only re-arm when the
 * helper is enabled, boot-start is opted into, and the overlay permission is
 * still held.
 */
fun shouldStartOnBoot(enabled: Boolean, autoStart: Boolean, canDrawOverlays: Boolean): Boolean =
    enabled && autoStart && canDrawOverlays

/**
 * Re-arms the overlay after a reboot — opt-in only. It starts the service only
 * when the helper is enabled, boot-start is turned on, and the overlay
 * permission is still held. Otherwise it does nothing: no crash, no silent
 * permission re-request. (`exported=false`; only the system delivers BOOT.)
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON"
        ) {
            return
        }

        val prefs = EdgePreferences(context)
        if (!shouldStartOnBoot(prefs.enabled, prefs.autoStart, Settings.canDrawOverlays(context))) return

        EdgeOverlayService.start(context)
    }
}
