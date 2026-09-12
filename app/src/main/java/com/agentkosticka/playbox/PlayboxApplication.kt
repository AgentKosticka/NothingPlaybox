package com.agentkosticka.playbox

import android.app.Application
import com.agentkosticka.playbox.data.EffectRepository
import com.agentkosticka.playbox.matrix.GlyphMatrixConnection
import com.agentkosticka.playbox.matrix.GlyphMatrixClient

class PlayboxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        com.agentkosticka.playbox.widget.WidgetPaletteMonitor(this).start()
        com.agentkosticka.playbox.widget.WidgetInstanceSettings(this).snapshotInstalled()
        // Battery broadcasts cannot wake a manifest receiver on modern Android. Observe them
        // while the process exists; periodic widget work remains the background fallback.
        registerReceiver(object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
                val now = java.time.ZonedDateTime.now()
                if (com.agentkosticka.playbox.widget.DashboardWidget.hasWidgets(context)) {
                    com.agentkosticka.playbox.widget.DashboardWidget.updateAll(context, now)
                }
                if (com.agentkosticka.playbox.widget.UtilityDashboardWidget.hasWidgets(context)) {
                    // Only redraw utility widgets that actually depend on battery state.
                    com.agentkosticka.playbox.widget.UtilityDashboardWidget.updateBatteryWidgets(context, now)
                }
            }
        }, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
    }

    val aodSettings by lazy { com.agentkosticka.playbox.data.AodSettingsStore(this) }
    val repository: EffectRepository by lazy { EffectRepository(this) }
    val glyphConnection: GlyphMatrixConnection by lazy { GlyphMatrixConnection(this) }
    val glyphClient: GlyphMatrixClient by lazy { GlyphMatrixClient(this, glyphConnection) }
}
