package app.edgehatch.launcher

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Optional overlay path for locked-down devices (e.g. the DJI RC 2) where the
 * SYSTEM_ALERT_WINDOW permission cannot be granted but an accessibility service
 * can be enabled. When connected, it draws the same edge handle and app panel as
 * [EdgeOverlayService], but via [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY],
 * which needs no overlay permission.
 *
 * Privacy: it does NOT read window content or inspect accessibility events
 * (`canRetrieveWindowContent=false`, [onAccessibilityEvent] is a no-op). The
 * service is used only as a permitted way to host an overlay. When it is
 * connected it becomes the active drawer and [EdgeOverlayService] yields to it,
 * so the handle is never drawn twice.
 */
class EdgeAccessibilityService : AccessibilityService() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: EdgePreferences
    private lateinit var repo: LauncherAppRepository

    private var handleView: EdgeHandleView? = null
    private var panelView: EdgePanelView? = null
    private var currentSide: HandleSide? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = EdgePreferences(this)
        repo = LauncherAppRepository(this)
        instance = this
        // The FGS path, if running, must yield to us — stop it outright so the
        // handle is never drawn by both. stopService is deterministic here
        // (no START_STICKY relaunch, unlike an ACTION_STOP self-signal).
        runCatching { stopService(Intent(this, EdgeOverlayService::class.java)) }
        refresh()
    }

    /** Reconcile the overlay with the current config. Called on connect + on change. */
    fun refresh() {
        if (instance !== this) return
        val config = prefs.snapshot()
        if (!config.enabled) {
            removeAll()
            return
        }
        val handle = handleView
        if (handle == null || currentSide != config.side) {
            addHandle(config)
        } else {
            updateHandle(handle, config)
        }
        panelView?.updateApps(repo.loadSelectedApps(config.selected))
    }

    private fun addHandle(config: EdgeConfig) {
        removeHandle()
        val onLeft = config.side == HandleSide.LEFT
        val view = EdgeHandleView(this, onLeft) { openPanel() }.apply {
            tapEnabled = config.triggerTap
            swipeEnabled = config.triggerSwipe
            alpha = config.opacity
        }
        runCatching { windowManager.addView(view, handleParams(config)) }
            .onSuccess {
                handleView = view
                currentSide = config.side
            }
    }

    private fun updateHandle(handle: EdgeHandleView, config: EdgeConfig) {
        handle.tapEnabled = config.triggerTap
        handle.swipeEnabled = config.triggerSwipe
        handle.alpha = config.opacity
        runCatching { windowManager.updateViewLayout(handle, handleParams(config)) }
    }

    private fun handleParams(config: EdgeConfig): WindowManager.LayoutParams {
        val onLeft = config.side == HandleSide.LEFT
        val widthPx = dp(config.widthDp)
        val heightPx = dp(config.heightDp)
        return WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (onLeft) Gravity.START else Gravity.END) or Gravity.TOP
            y = (config.verticalBias * (screenHeight() - heightPx).coerceAtLeast(0)).toInt()
        }
    }

    private fun openPanel() {
        if (panelView != null) return
        // Themed context: EdgePanelView inflates Material views that need the app
        // theme; a raw service context would crash the inflation. The whole build
        // is guarded so a failure can never take the process down.
        runCatching {
            val config = prefs.snapshot()
            val onLeft = config.side == HandleSide.LEFT
            val apps = repo.loadSelectedApps(config.selected)
            val themed = ContextThemeWrapper(this, R.style.Theme_EdgeHatch)
            val view = EdgePanelView(
                context = themed,
                apps = apps,
                onLeft = onLeft,
                onAppSelected = { entry -> launchApp(entry); closePanel() },
                onDismiss = { closePanel() },
            )
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            )
            windowManager.addView(view, params)
            panelView = view
        }.onFailure { closePanel() }
    }

    private fun closePanel() {
        panelView?.let { view -> runCatching { windowManager.removeView(view) } }
        panelView = null
    }

    private fun launchApp(entry: AppEntry) {
        val launch = repo.launchIntentFor(entry.packageName) ?: return
        runCatching { startActivity(launch) }
    }

    private fun removeHandle() {
        handleView?.let { view -> runCatching { windowManager.removeView(view) } }
        handleView = null
        currentSide = null
    }

    private fun removeAll() {
        closePanel()
        removeHandle()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: we never inspect events or window content.
    }

    override fun onInterrupt() {
        // No feedback to interrupt.
    }

    override fun onUnbind(intent: Intent?): Boolean {
        removeAll()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        // Belt-and-braces: some Android versions destroy without a prior unbind.
        removeAll()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    companion object {
        @Volatile
        var instance: EdgeAccessibilityService? = null
            private set

        /** True while the service is connected and can draw without overlay permission. */
        fun isConnected(): Boolean = instance != null

        /** Reconcile the accessibility overlay after a config/enable change, if connected. */
        fun refreshIfConnected() {
            instance?.refresh()
        }
    }
}
