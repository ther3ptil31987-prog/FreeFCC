package com.freefcc.edge

import android.content.Context
import android.content.SharedPreferences

enum class HandleSide { LEFT, RIGHT }

/**
 * An immutable, atomically-read snapshot of every live parameter, tagged with a
 * monotonic [version] so a stale apply can never overwrite a newer one.
 */
data class EdgeConfig(
    val version: Long,
    val enabled: Boolean,
    val side: HandleSide,
    val verticalBias: Float,
    val widthDp: Int,
    val heightDp: Int,
    val opacity: Float,
    val triggerTap: Boolean,
    val triggerSwipe: Boolean,
    val selected: Set<String>,
)

/** Tight clamps and safe defaults for every live parameter. */
object EdgeLimits {
    const val WIDTH_MIN = 6
    const val WIDTH_MAX = 40
    const val WIDTH_DEFAULT = 14

    const val HEIGHT_MIN = 48
    const val HEIGHT_MAX = 240
    const val HEIGHT_DEFAULT = 96

    const val OPACITY_MIN = 0.15f
    const val OPACITY_MAX = 1.0f
    const val OPACITY_DEFAULT = 0.40f

    const val BIAS_DEFAULT = 0.5f

    fun clampWidthDp(v: Int): Int = v.coerceIn(WIDTH_MIN, WIDTH_MAX)
    fun clampHeightDp(v: Int): Int = v.coerceIn(HEIGHT_MIN, HEIGHT_MAX)
    fun clampOpacity(v: Float): Float = if (v.isNaN()) OPACITY_DEFAULT else v.coerceIn(OPACITY_MIN, OPACITY_MAX)
    fun clampBias(v: Float): Float = if (v.isNaN()) BIAS_DEFAULT else v.coerceIn(0f, 1f)

    /** At least one trigger must stay on, or the handle would be dead. */
    fun safeTriggers(tap: Boolean, swipe: Boolean): Pair<Boolean, Boolean> =
        if (!tap && !swipe) true to false else tap to swipe
}

/**
 * All persisted state for the edge helper. Small and self-contained: an opt-in
 * enable flag, opt-in boot start, the handle's side/position/size/opacity, the
 * tap/swipe triggers, and the apps shown in the panel. Every mutation of a live
 * parameter bumps [configVersion] so the service can reject stale snapshots.
 * No automation/accessibility settings exist here.
 */
class EdgePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply().also { bump() }

    /** Not a live parameter: stored only, applied at the next boot. */
    var autoStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_START, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_START, value).apply()

    var handleSide: HandleSide
        get() = if (prefs.getString(KEY_SIDE, SIDE_RIGHT) == SIDE_LEFT) HandleSide.LEFT else HandleSide.RIGHT
        set(value) {
            prefs.edit().putString(KEY_SIDE, if (value == HandleSide.LEFT) SIDE_LEFT else SIDE_RIGHT).apply()
            bump()
        }

    var handleVerticalBias: Float
        get() = EdgeLimits.clampBias(prefs.getFloat(KEY_BIAS, EdgeLimits.BIAS_DEFAULT))
        set(value) { prefs.edit().putFloat(KEY_BIAS, EdgeLimits.clampBias(value)).apply(); bump() }

    var handleWidthDp: Int
        get() = EdgeLimits.clampWidthDp(prefs.getInt(KEY_WIDTH, EdgeLimits.WIDTH_DEFAULT))
        set(value) { prefs.edit().putInt(KEY_WIDTH, EdgeLimits.clampWidthDp(value)).apply(); bump() }

    var handleHeightDp: Int
        get() = EdgeLimits.clampHeightDp(prefs.getInt(KEY_HEIGHT, EdgeLimits.HEIGHT_DEFAULT))
        set(value) { prefs.edit().putInt(KEY_HEIGHT, EdgeLimits.clampHeightDp(value)).apply(); bump() }

    var handleOpacity: Float
        get() = EdgeLimits.clampOpacity(prefs.getFloat(KEY_OPACITY, EdgeLimits.OPACITY_DEFAULT))
        set(value) { prefs.edit().putFloat(KEY_OPACITY, EdgeLimits.clampOpacity(value)).apply(); bump() }

    var triggerTap: Boolean
        get() = prefs.getBoolean(KEY_TRIGGER_TAP, true)
        set(value) { prefs.edit().putBoolean(KEY_TRIGGER_TAP, value).apply(); bump() }

    var triggerSwipe: Boolean
        get() = prefs.getBoolean(KEY_TRIGGER_SWIPE, true)
        set(value) { prefs.edit().putBoolean(KEY_TRIGGER_SWIPE, value).apply(); bump() }

    var selectedPackages: Set<String>
        // Copy on read so callers can't mutate the backing set (SharedPreferences contract).
        get() = HashSet(prefs.getStringSet(KEY_SELECTED, emptySet()) ?: emptySet())
        set(value) { prefs.edit().putStringSet(KEY_SELECTED, value).apply(); bump() }

    val configVersion: Long
        get() = prefs.getLong(KEY_VERSION, 0L)

    private fun bump() {
        prefs.edit().putLong(KEY_VERSION, prefs.getLong(KEY_VERSION, 0L) + 1L).apply()
    }

    /** Read every live value at once, tagged with the current version. */
    fun snapshot(): EdgeConfig {
        val (tap, swipe) = EdgeLimits.safeTriggers(triggerTap, triggerSwipe)
        return EdgeConfig(
            version = configVersion,
            enabled = enabled,
            side = handleSide,
            verticalBias = handleVerticalBias,
            widthDp = handleWidthDp,
            heightDp = handleHeightDp,
            opacity = handleOpacity,
            triggerTap = tap,
            triggerSwipe = swipe,
            selected = selectedPackages,
        )
    }

    companion object {
        private const val FILE = "freefcc_edge"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_SIDE = "handle_side"
        private const val KEY_BIAS = "handle_bias"
        private const val KEY_WIDTH = "handle_width"
        private const val KEY_HEIGHT = "handle_height"
        private const val KEY_OPACITY = "handle_opacity"
        private const val KEY_TRIGGER_TAP = "trigger_tap"
        private const val KEY_TRIGGER_SWIPE = "trigger_swipe"
        private const val KEY_SELECTED = "selected_packages"
        private const val KEY_VERSION = "config_version"
        private const val SIDE_LEFT = "left"
        private const val SIDE_RIGHT = "right"
    }
}
