package com.freefcc.edge

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/**
 * Application entry point. Creates the low-importance notification channel the
 * foreground overlay service needs. No image-loading, accessibility, or
 * automation initialisation.
 */
class SidePanelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "freefcc_edge_overlay"
    }
}
