package com.agentkosticka.playbox.widget

import android.app.Activity
import android.app.Application
import android.content.ComponentCallbacks
import android.content.res.Configuration
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/** One process-wide observer; resources are authoritative, notifications only trigger checks. */
class WidgetPaletteMonitor(private val app: Application) {
    private val handler = Handler(Looper.getMainLooper())
    private var lastPalette: List<WidgetPalette>? = null
    private val verify = Runnable {
        val palette = listOf(WidgetPalette.resolve(app, false), WidgetPalette.resolve(app, true))
        if (palette != lastPalette) {
            lastPalette = palette
            InstanceWidgetProvider.refreshAll(app)
        }
    }

    private fun scheduleCheck() {
        handler.removeCallbacks(verify)
        // Picker metadata can change before SystemUI finishes installing its overlays.
        for (delay in listOf(0L, 300L, 1000L, 3000L)) handler.postDelayed(verify, delay)
    }

    fun start() {
        WidgetPalette.initialize(app)
        app.registerComponentCallbacks(object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) = scheduleCheck()
            override fun onLowMemory() = Unit
        })
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) = scheduleCheck()
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // Optional AOSP signal. Lifecycle/configuration checks remain available if restricted.
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.Secure.getUriFor("theme_customization_overlay_packages"), false,
                object : ContentObserver(handler) {
                    override fun onChange(selfChange: Boolean) = scheduleCheck()
                }
            )
        }
        scheduleCheck()
    }
}
