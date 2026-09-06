package com.agentkosticka.playbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.MainActivity
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
                    val bitmap = if (provider == DayDialWidget::class.java) DashboardRenderer.dayDial(now) else DashboardRenderer.battery(battery.first, battery.second)
                    val views = RemoteViews(context.packageName, R.layout.widget_time_bars)
                    views.setImageViewBitmap(R.id.time_bars_image, bitmap)
                    views.setContentDescription(R.id.time_bars_image, if (provider == DayDialWidget::class.java) "${(timeProgress(now)[0].fraction * 100).toInt()} percent of today elapsed" else "Battery ${battery.first?.toString() ?: "unknown"} percent, ${if (battery.second) "charging" else "on battery"}")
                    views.setOnClickPendingIntent(R.id.time_bars_image, PendingIntent.getActivity(context, 0,
                        Intent(context, MainActivity::class.java).putExtra("open_widgets", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
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
    private fun base(): Pair<Bitmap, Canvas> {
        val bitmap = createBitmap(360, 360)
        val canvas = Canvas(bitmap)
        canvas.drawRoundRect(0f, 0f, 360f, 360f, 32f, 32f, Paint().apply { color = Color.rgb(17, 17, 17) })
        return bitmap to canvas
    }
    private fun label(canvas: Canvas, text: String, y: Float, step: Float, color: Int = Color.WHITE) {
        TimeBarsRenderer.dotText(canvas, text, (360 - (text.length * 6 - 1) * step) / 2, y, step, color)
    }
    fun dayDial(now: ZonedDateTime): Bitmap {
        val (bitmap, canvas) = base()
        val fraction = timeProgress(now)[0].fraction
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(60) { dot ->
            val angle = dot * PI / 30 - PI / 2
            paint.color = if (dot < floor(fraction * 60)) Color.WHITE else Color.DKGRAY
            canvas.drawCircle(180 + cos(angle).toFloat() * 145, 180 + sin(angle).toFloat() * 145, 3.8f, paint)
        }
        label(canvas, now.dayOfWeek.name.take(3), 104f, 4f)
        label(canvas, "${(fraction * 100).toInt()}%", 155f, 7f)
        label(canvas, "${now.dayOfMonth} ${now.month.name.take(3)}", 223f, 3.5f, Color.LTGRAY)
        return bitmap
    }
    fun battery(percent: Int?, charging: Boolean): Bitmap {
        val (bitmap, canvas) = base()
        label(canvas, "BATTERY", 25f, 4f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        repeat(100) { dot ->
            paint.color = if (dot < (percent ?: 0)) Color.WHITE else Color.DKGRAY
            canvas.drawCircle(90f + dot % 10 * 20, 83f + dot / 10 * 16, 3.7f, paint)
        }
        label(canvas, percent?.let { "$it%" } ?: "UNKNOWN", 251f, if (percent == null) 4f else 6f)
        label(canvas, if (charging) "CHARGING" else "ON BATTERY", 315f, 2.8f, Color.LTGRAY)
        return bitmap
    }
}
