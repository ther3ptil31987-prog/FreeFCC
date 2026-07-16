package com.freefcc.edge

import android.graphics.drawable.Drawable

/** A single launchable app shown in the edge panel. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val icon: Drawable,
)
