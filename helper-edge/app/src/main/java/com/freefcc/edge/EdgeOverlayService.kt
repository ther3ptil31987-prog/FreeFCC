package com.freefcc.edge

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager

/**
 * Pure version guard: apply a config snapshot only if it is newer than the last
 * one applied, so a stale/queued apply can never roll back a newer value.
 */
fun shouldApplySnapshot(incomingVersion: Long, appliedVersion: Long): Boolean =
    incomingVersion > appliedVersion

/**
 * Foreground overlay service. It owns the [WindowManager], draws the edge
 * handle, and opens the app panel on trigger. It is NOT gated on any
 * accessibility service or automation engine — the handle shows whenever the
 * helper is enabled and the overlay permission is held.
 *
 * Live config: [MainActivity] persists a value then sends an explicit,
 * non-exported [ACTION_APPLY_CONFIGURATION]. The service loads a full
 * [EdgeConfig] snapshot and updates the resting handle in place with
 * [WindowManager.updateViewLayout]; only a side change removes/re-adds its own
 * handle. An open panel refreshes its list without a restart. A newer snapshot
 * always wins ([shouldApplySnapshot]).
 *
 * Fail-safe: if the overlay permission is missing or revoked, it posts the
 * mandatory foreground notification and stops cleanly. All views are removed on
 * stop/destroy.
 *
 * The WindowManager overlay parameters (TYPE_APPLICATION_OVERLAY + the
 * NOT_FOCUSABLE | NOT_TOUCH_MODAL | LAYOUT_IN_SCREEN flag set for a pass-through
 * edge handle) are adapted from Smart-Edge (Imtiaz-Official/Smart-Edge,
 * FloatingPanelService.addEdgeHandle, commit c54beef, MIT © 2024 Imtiaz).
 */
class EdgeOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: EdgePreferences
    private lateinit var repo: LauncherAppRepository

    private var handleView: EdgeHandleView? = null
    private var panelView: EdgePanelView? = null

    private var appliedVersion = -1L
    private var currentSide: HandleSide? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = EdgePreferences(this)
        repo = LauncherAppRepository(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())

        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Fail-safe: without overlay permission we cannot draw anything.
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        applyConfiguration(prefs.snapshot())
        return START_STICKY
    }

    /** Load a snapshot and reconcile the overlay with it. */
    private fun applyConfiguration(config: EdgeConfig) {
        if (!shouldApplySnapshot(config.version, appliedVersion)) return
        appliedVersion = config.version

        if (!config.enabled) {
            stopSelf()
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        val handle = handleView
        if (handle == null || currentSide != config.side) {
            // First show, or a side change: rebuild only our own handle.
            addHandle(config)
        } else {
            updateHandle(handle, config)
        }
        // Refresh an open panel's list live.
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
            .onFailure { stopSelf() }
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
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
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
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }
        val config = prefs.snapshot()
        val onLeft = config.side == HandleSide.LEFT
        val apps = repo.loadSelectedApps(config.selected)
        val view = EdgePanelView(
            context = this,
            apps = apps,
            onLeft = onLeft,
            onAppSelected = { entry -> launchApp(entry); closePanel() },
            onDismiss = { closePanel() },
        )
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching { windowManager.addView(view, params) }
            .onSuccess { panelView = view }
            .onFailure { closePanel() }
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

    private fun removeAllViews() {
        closePanel()
        removeHandle()
    }

    override fun onDestroy() {
        removeAllViews()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun screenHeight(): Int = resources.displayMetrics.heightPixels

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, EdgeOverlayService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, SidePanelApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.drawable.ic_edge_notification)
            .addAction(
                Notification.Action.Builder(null, getString(R.string.notif_stop), stopPending).build(),
            )
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.freefcc.edge.action.STOP"
        const val ACTION_APPLY_CONFIGURATION = "com.freefcc.edge.action.APPLY_CONFIGURATION"

        /** Start the overlay (or deliver a fresh config) as a foreground service. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, EdgeOverlayService::class.java))
        }

        /** Persisted-value change: reconcile the running overlay without a restart. */
        fun applyConfiguration(context: Context) {
            context.startForegroundService(
                Intent(context, EdgeOverlayService::class.java).setAction(ACTION_APPLY_CONFIGURATION),
            )
        }

        /** Ask the running service to stop and remove its views. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, EdgeOverlayService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
