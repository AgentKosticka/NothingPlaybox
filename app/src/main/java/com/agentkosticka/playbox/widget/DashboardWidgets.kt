package com.agentkosticka.playbox.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.BatteryManager
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.R
import java.time.ZonedDateTime
import kotlin.math.*

class DayDialWidget : DashboardWidget()
class BatteryDotsWidget : DashboardWidget()

open class DashboardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        TimeBarsWidget.updateAll(context)
        TimeBarsWidget.schedule(context)
    }
    override fun onDisabled(context: Context) { TimeBarsWidget.cancelIfUnused(context) }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_POWER_CONNECTED, Intent.ACTION_POWER_DISCONNECTED, Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY)) {
            val now = ZonedDateTime.now()
            updateAll(context, now)
            UtilityDashboardWidget.updateBatteryWidgets(context, now)
        }
    }
    companion object {
        val providers = listOf(DayDialWidget::class.java, BatteryDotsWidget::class.java)
        fun hasWidgets(context: Context) = providers.any { AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        fun updateAll(context: Context, now: ZonedDateTime) {
            val manager = AppWidgetManager.getInstance(context)
            providers.forEach { provider ->
                val ids = manager.getAppWidgetIds(ComponentName(context, provider))
                if (ids.isNotEmpty()) {
                    val battery = batteryStatus(context)
                    val views = RemoteViews(context.packageName, R.layout.widget_time_bars)
                    views.setThemedWidgetBitmap(context, R.id.time_bars_image) { palette ->
                        if (provider == DayDialWidget::class.java) {
                            DashboardRenderer.dayDial(now, palette)
                        } else {
                            DashboardRenderer.battery(battery.first, battery.second, palette)
                        }
                    }
                    val contentDescription = if (provider == DayDialWidget::class.java) {
                        context.getString(R.string.widget_day_progress_cd, (timeProgress(now)[0].fraction * 100).toInt())
                    } else {
                        val status = context.getString(
                            if (battery.second) R.string.widget_battery_charging else R.string.widget_battery_on_battery,
                        )
                        battery.first?.let { percent ->
                            context.getString(R.string.widget_battery_percent_cd, percent, status)
                        } ?: context.getString(R.string.widget_battery_unknown_cd, status)
                    }
                    views.setContentDescription(R.id.time_bars_image, contentDescription)
                    views.setOnClickPendingIntent(R.id.time_bars_image, widgetPendingIntent(context, WidgetDestination.forProvider(provider)))
                    manager.updateAppWidget(ids, views)
                }
            }
        }
    }
}

fun batteryStatus(context: Context): Pair<Int?, Boolean> {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null to false
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val percent = if (level >= 0 && scale > 0) (level * 100.0 / scale).roundToInt().coerceIn(0, 100) else null
    return percent to (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
}

object DashboardRenderer {
    private fun base(palette: WidgetPalette): Pair<Bitmap, Canvas> {
        val bitmap = createBitmap(360, 360)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, 360f, 360f, 32f, 32f, Paint().apply { color = palette.background })
        return bitmap to canvas
    }

    private fun label(canvas: Canvas, text: String, y: Float, step: Float, color: Int) {
        TimeBarsRenderer.dotText(canvas, text, (360 - (text.length * 6 - 1) * step) / 2, y, step, color)
    }

    fun dayDial(now: ZonedDateTime, palette: WidgetPalette = WidgetPalette.current()): Bitmap {
        val (bitmap, canvas) = base(palette)
        val fraction = timeProgress(now)[0].fraction
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(60) { dot ->
            val angle = dot * PI / 30 - PI / 2
            paint.color = if (dot < floor(fraction * 60)) palette.foreground else palette.inactive
            canvas.drawCircle(180 + cos(angle).toFloat() * 145, 180 + sin(angle).toFloat() * 145, 3.8f, paint)
        }
        label(canvas, now.dayOfWeek.name.take(3), 104f, 4f, palette.foreground)
        label(canvas, "${(fraction * 100).toInt()}%", 155f, 7f, palette.foreground)
        label(canvas, "${now.dayOfMonth} ${now.month.name.take(3)}", 223f, 3.5f, palette.muted)
        return bitmap
    }

    fun battery(percent: Int?, charging: Boolean, palette: WidgetPalette = WidgetPalette.current()): Bitmap {
        val (bitmap, canvas) = base(palette)
        label(canvas, "BATTERY", 25f, 4f, palette.foreground)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(100) { dot ->
            paint.color = if (dot < (percent ?: 0)) palette.foreground else palette.inactive
            canvas.drawCircle(90f + dot % 10 * 20, 83f + dot / 10 * 16, 3.7f, paint)
        }
        label(canvas, percent?.let { "$it%" } ?: "UNKNOWN", 251f, if (percent == null) 4f else 6f, palette.foreground)
        label(canvas, if (charging) "CHARGING" else "ON BATTERY", 315f, 2.8f, if (charging) palette.accent else palette.muted)
        return bitmap
    }
}
