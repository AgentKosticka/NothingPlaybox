package com.agentkosticka.playbox.widget

import android.app.AlarmManager
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
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.text.format.DateFormat
import android.widget.RemoteViews
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.MainActivity
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.ui.NothingDotFont
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

class BatteryGlyphWidget : UtilityDashboardWidget()
class NextAlarmWidget : UtilityDashboardWidget()
class StorageMatrixWidget : UtilityDashboardWidget()
class MonthMatrixWidget : UtilityDashboardWidget()
class WeekStripWidget : UtilityDashboardWidget()
class YearDotsWidget : UtilityDashboardWidget()
class DevicePanelWidget : UtilityDashboardWidget()
class MilestoneWidget : UtilityDashboardWidget()

class NDotClockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_ndot_clock)
            views.setOnClickPendingIntent(R.id.ndot_clock_root, widgetsPendingIntent(context))
            manager.updateAppWidget(id, views)
        }
    }
}

/** Home-screen entry points for the Playbox editor and Nothing's own AOD Toy selector. */
class PlayboxShortcutsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_playbox_shortcuts)
            views.setOnClickPendingIntent(
                R.id.shortcut_matrix,
                PendingIntent.getActivity(
                    context,
                    100,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            views.setOnClickPendingIntent(
                R.id.shortcut_widgets,
                PendingIntent.getActivity(
                    context,
                    101,
                    Intent(context, MainActivity::class.java).putExtra("open_widgets", true),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            val aodIntent = Intent().apply {
                component = ComponentName(
                    "com.nothing.thirdparty",
                    "com.nothing.thirdparty.matrix.toys.manager.AodToySelectActivity",
                )
            }
            views.setOnClickPendingIntent(
                R.id.shortcut_aod,
                PendingIntent.getActivity(
                    context,
                    102,
                    aodIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            manager.updateAppWidget(id, views)
        }
    }
}

open class UtilityDashboardWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        TimeBarsWidget.updateAll(context)
        TimeBarsWidget.schedule(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        updateAll(context, ZonedDateTime.now())
    }

    override fun onDisabled(context: Context) {
        TimeBarsWidget.cancelIfUnused(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED -> updateAlarmWidgets(context, ZonedDateTime.now())
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            Intent.ACTION_BATTERY_LOW,
            Intent.ACTION_BATTERY_OKAY,
            -> updateBatteryWidgets(context, ZonedDateTime.now())
        }
    }

    companion object {
        val providers: List<Class<out UtilityDashboardWidget>> = listOf(
            BatteryGlyphWidget::class.java,
            NextAlarmWidget::class.java,
            StorageMatrixWidget::class.java,
            MonthMatrixWidget::class.java,
            WeekStripWidget::class.java,
            YearDotsWidget::class.java,
            DevicePanelWidget::class.java,
            MilestoneWidget::class.java,
        )

        fun hasWidgets(context: Context): Boolean = providers.any {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }

        fun updateAll(context: Context, now: ZonedDateTime) = updateProviders(context, now, providers)

        fun updateBatteryWidgets(context: Context, now: ZonedDateTime) = updateProviders(
            context,
            now,
            listOf(BatteryGlyphWidget::class.java, DevicePanelWidget::class.java),
        )

        private fun updateAlarmWidgets(context: Context, now: ZonedDateTime) = updateProviders(
            context,
            now,
            listOf(NextAlarmWidget::class.java, DevicePanelWidget::class.java),
        )

        private fun updateProviders(
            context: Context,
            now: ZonedDateTime,
            selectedProviders: List<Class<out UtilityDashboardWidget>>,
        ) {
            val manager = AppWidgetManager.getInstance(context)
            val utility = UtilityWidgetSettings.load(context)
            val time = TimeBarsSettings.load(context)
            val battery = batteryInfo(context)
            val storage = storageInfo()
            val alarm = nextAlarm(context, now)

            selectedProviders.forEach { provider ->
                manager.getAppWidgetIds(ComponentName(context, provider)).forEach { id ->
                    val options = manager.getAppWidgetOptions(id)
                    val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, defaultWidth(provider))
                    val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, defaultHeight(provider))
                    val (width, height) = UtilityWidgetRenderer.bitmapSize(widthDp, heightDp)
                    val bitmap = when (provider) {
                        BatteryGlyphWidget::class.java -> UtilityWidgetRenderer.batteryGlyph(battery, utility.batteryVisual, width, height)
                        NextAlarmWidget::class.java -> UtilityWidgetRenderer.nextAlarm(context, now, alarm, width, height)
                        StorageMatrixWidget::class.java -> UtilityWidgetRenderer.storage(storage, utility.storageDisplay, width, height)
                        MonthMatrixWidget::class.java -> UtilityWidgetRenderer.month(now, time.weekStart, width, height)
                        WeekStripWidget::class.java -> UtilityWidgetRenderer.weekStrip(now, time.weekStart, width, height)
                        YearDotsWidget::class.java -> UtilityWidgetRenderer.year(now, utility.yearDisplay, width, height)
                        DevicePanelWidget::class.java -> UtilityWidgetRenderer.devicePanel(context, now, battery, storage, alarm, width, height)
                        MilestoneWidget::class.java -> UtilityWidgetRenderer.milestone(now, utility.milestoneTarget, width, height)
                        else -> UtilityWidgetRenderer.milestone(now, MilestoneTarget.WEEKEND, width, height)
                    }
                    val views = RemoteViews(context.packageName, R.layout.widget_time_bars)
                    views.setImageViewBitmap(R.id.time_bars_image, bitmap)
                    views.setContentDescription(
                        R.id.time_bars_image,
                        contentDescription(provider, context, now, battery, storage, alarm, utility),
                    )
                    views.setOnClickPendingIntent(R.id.time_bars_image, widgetsPendingIntent(context))
                    manager.updateAppWidget(id, views)
                }
            }
        }

        private fun defaultWidth(provider: Class<out UtilityDashboardWidget>): Int = when (provider) {
            BatteryGlyphWidget::class.java, NextAlarmWidget::class.java, MilestoneWidget::class.java -> 110
            else -> 250
        }

        private fun defaultHeight(provider: Class<out UtilityDashboardWidget>): Int = when (provider) {
            BatteryGlyphWidget::class.java, NextAlarmWidget::class.java, WeekStripWidget::class.java -> 55
            else -> 110
        }

        private fun contentDescription(
            provider: Class<out UtilityDashboardWidget>,
            context: Context,
            now: ZonedDateTime,
            battery: BatteryInfo,
            storage: StorageInfo?,
            alarm: ZonedDateTime?,
            settings: UtilityWidgetSettings,
        ): String = when (provider) {
            BatteryGlyphWidget::class.java -> "Battery ${battery.percent ?: 0} percent, ${if (battery.charging) "charging" else "on battery"}"
            NextAlarmWidget::class.java -> alarm?.let { "Next alarm ${formatAlarmTime(context, it)} ${alarmDayLabel(now, it)}" } ?: "No alarm set"
            StorageMatrixWidget::class.java -> storage?.let {
                val fraction = if (settings.storageDisplay == StorageDisplay.FREE) it.freeFraction else 1.0 - it.freeFraction
                "Storage ${settings.storageDisplay.label.lowercase(Locale.ROOT)} ${(fraction * 100).roundToInt()} percent"
            } ?: "Storage unavailable"
            MonthMatrixWidget::class.java -> "Calendar for ${now.month.name.lowercase(Locale.ROOT)} ${now.year}, today is ${now.dayOfMonth}"
            WeekStripWidget::class.java -> "Current week, today is ${now.dayOfWeek.name.lowercase(Locale.ROOT)} ${now.dayOfMonth}"
            YearDotsWidget::class.java -> "${settings.yearDisplay.label} days in ${now.year}"
            DevicePanelWidget::class.java -> "Device status: battery, free storage, next alarm and today progress"
            MilestoneWidget::class.java -> "Countdown to ${settings.milestoneTarget.label.lowercase(Locale.ROOT)}"
            else -> "Nothing Playbox widget"
        }
    }
}

private fun widgetsPendingIntent(context: Context): PendingIntent = PendingIntent.getActivity(
    context,
    0,
    Intent(context, MainActivity::class.java).putExtra("open_widgets", true),
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
)

data class BatteryInfo(val percent: Int?, val charging: Boolean, val chargeRemainingMs: Long?)

data class StorageInfo(val totalBytes: Long, val freeBytes: Long) {
    val freeFraction: Double get() = if (totalBytes > 0) freeBytes.toDouble() / totalBytes else 0.0
}

fun batteryInfo(context: Context): BatteryInfo {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val percent = if (level >= 0 && scale > 0) (level * 100.0 / scale).roundToInt().coerceIn(0, 100) else null
    val charging = (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    val remaining = if (charging) {
        context.getSystemService(BatteryManager::class.java)?.computeChargeTimeRemaining()?.takeIf { it > 0 }
    } else null
    return BatteryInfo(percent, charging, remaining)
}

fun storageInfo(): StorageInfo? = runCatching {
    val stats = StatFs(Environment.getDataDirectory().absolutePath)
    StorageInfo(
        totalBytes = stats.blockCountLong * stats.blockSizeLong,
        freeBytes = stats.availableBlocksLong * stats.blockSizeLong,
    )
}.getOrNull()

fun nextAlarm(context: Context, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime? {
    val trigger = context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime ?: return null
    return ZonedDateTime.ofInstant(Instant.ofEpochMilli(trigger), now.zone)
}

private fun formatAlarmTime(context: Context, alarm: ZonedDateTime): String = alarm.format(
    DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", Locale.getDefault()),
)

private fun alarmDayLabel(now: ZonedDateTime, alarm: ZonedDateTime): String = when (alarm.toLocalDate()) {
    now.toLocalDate() -> "TODAY"
    now.toLocalDate().plusDays(1) -> "TOMORROW"
    else -> alarm.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase(Locale.getDefault())
}

object UtilityWidgetRenderer {
    private const val BG = 0xFF111111.toInt()
    private const val MID = 0xFF393939.toInt()
    private const val DIM = 0xFF242424.toInt()
    private const val RED = 0xFFD71920.toInt()

    fun bitmapSize(widthDp: Int, heightDp: Int): Pair<Int, Int> {
        val ratio = (widthDp.coerceAtLeast(55).toFloat() / heightDp.coerceAtLeast(55)).coerceIn(.75f, 3f)
        val height = if (ratio > 1.45f) 320 else 360
        return (height * ratio).roundToInt().coerceIn(320, 960) to height
    }

    private fun base(width: Int, height: Int): Pair<Bitmap, Canvas> {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val radius = min(width, height) * .09f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BG })
        return bitmap to canvas
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int = Color.WHITE,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        canvas.drawText(value, x, baseline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = NothingDotFont.typeface
            textAlign = align
            isSubpixelText = true
        })
    }

    private fun fittedText(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        maxWidth: Float,
        maxSize: Float,
        color: Int = Color.WHITE,
        align: Paint.Align = Paint.Align.LEFT,
    ) {
        var size = maxSize
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = NothingDotFont.typeface }
        while (size > 12f) {
            paint.textSize = size
            if (paint.measureText(value) <= maxWidth) break
            size -= 2f
        }
        text(canvas, value, x, baseline, size, color, align)
    }

    private fun header(canvas: Canvas, value: String, width: Int, height: Int) {
        text(canvas, value, width * .07f, height * .17f, min(width, height) * .075f, Color.LTGRAY)
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, radius: Float, filled: Boolean, accent: Boolean = false) {
        canvas.drawCircle(x, y, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                accent -> RED
                filled -> Color.WHITE
                else -> MID
            }
        })
    }

    private fun dottedBar(canvas: Canvas, fraction: Double, left: Float, right: Float, y: Float, count: Int = 32) {
        val safeCount = count.coerceAtLeast(2)
        val step = (right - left) / (safeCount - 1)
        val filled = floor(fraction.coerceIn(0.0, 1.0) * safeCount).toInt()
        val radius = (step * .25f).coerceIn(2.3f, 5f)
        repeat(safeCount) { i -> dot(canvas, left + i * step, y, radius, i < filled) }
    }

    fun batteryGlyph(info: BatteryInfo, visual: BatteryVisual, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        header(canvas, "BATTERY GLYPH", width, height)
        val wide = width > height * 1.45f
        val percent = info.percent ?: 0
        val cx = if (wide) width * .25f else width * .5f
        val cy = if (wide) height * .56f else height * .48f
        val radius = min(width, height) * if (wide) .25f else .22f
        when (visual) {
            BatteryVisual.RING -> repeat(40) { i ->
                val angle = i * PI * 2 / 40 - PI / 2
                dot(
                    canvas,
                    cx + cos(angle).toFloat() * radius,
                    cy + sin(angle).toFloat() * radius,
                    min(width, height) * .012f,
                    i < percent * 40 / 100,
                    info.charging && i == percent * 40 / 100,
                )
            }
            BatteryVisual.DOTS -> repeat(25) { i ->
                dot(
                    canvas,
                    cx - radius * .7f + (i % 5) * radius * .35f,
                    cy - radius * .7f + (i / 5) * radius * .35f,
                    min(width, height) * .018f,
                    i < ceil(percent / 4.0).toInt(),
                )
            }
            BatteryVisual.BAR -> dottedBar(canvas, percent / 100.0, cx - radius, cx + radius, cy, 25)
        }
        val valueX = if (wide) width * .54f else width * .5f
        val valueY = if (wide) height * .59f else height * .80f
        text(canvas, info.percent?.let { "$it%" } ?: "--", valueX, valueY, min(width, height) * .19f, Color.WHITE, if (wide) Paint.Align.LEFT else Paint.Align.CENTER)
        val detail = when {
            info.charging && info.chargeRemainingMs != null -> "FULL IN ${formatDuration(info.chargeRemainingMs)}"
            info.charging -> "CHARGING"
            else -> "ON BATTERY"
        }
        fittedText(
            canvas,
            detail,
            if (wide) width * .55f else width * .5f,
            if (wide) height * .76f else height * .93f,
            if (wide) width * .39f else width * .82f,
            min(width, height) * .06f,
            if (info.charging) RED else Color.LTGRAY,
            if (wide) Paint.Align.LEFT else Paint.Align.CENTER,
        )
        return bitmap
    }

    fun nextAlarm(context: Context, now: ZonedDateTime, alarm: ZonedDateTime?, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        header(canvas, "NEXT ALARM", width, height)
        if (alarm == null) {
            text(canvas, "NO ALARM", width / 2f, height * .62f, min(width, height) * .15f, Color.WHITE, Paint.Align.CENTER)
            text(canvas, "NOTHING SCHEDULED", width / 2f, height * .82f, min(width, height) * .045f, Color.LTGRAY, Paint.Align.CENTER)
            return bitmap
        }
        fittedText(canvas, formatAlarmTime(context, alarm), width / 2f, height * .60f, width * .86f, min(width, height) * .28f, Color.WHITE, Paint.Align.CENTER)
        text(canvas, alarmDayLabel(now, alarm), width / 2f, height * .78f, min(width, height) * .07f, RED, Paint.Align.CENTER)
        val minutes = Duration.between(now, alarm).toMinutes().coerceAtLeast(0)
        val until = if (minutes < 60) "IN $minutes MIN" else "IN ${String.format(Locale.US, "%.1f", minutes / 60.0)} H"
        text(canvas, until, width / 2f, height * .91f, min(width, height) * .045f, Color.LTGRAY, Paint.Align.CENTER)
        return bitmap
    }

    fun storage(info: StorageInfo?, display: StorageDisplay, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        header(canvas, "STORAGE", width, height)
        if (info == null) {
            text(canvas, "UNAVAILABLE", width / 2f, height * .58f, min(width, height) * .12f, Color.WHITE, Paint.Align.CENTER)
            return bitmap
        }
        val fraction = if (display == StorageDisplay.FREE) info.freeFraction else 1.0 - info.freeFraction
        val selectedBytes = if (display == StorageDisplay.FREE) info.freeBytes else info.totalBytes - info.freeBytes
        val left = width * .12f
        val right = width * .88f
        val top = height * .30f
        val bottom = height * .66f
        repeat(100) { i ->
            dot(
                canvas,
                left + (i % 20) * (right - left) / 19f,
                top + (i / 20) * (bottom - top) / 4f,
                min(width, height) * .009f,
                i < (fraction * 100).roundToInt(),
            )
        }
        text(canvas, "${(fraction * 100).roundToInt()}% ${display.name}", width / 2f, height * .82f, min(width, height) * .085f, Color.WHITE, Paint.Align.CENTER)
        text(canvas, "${formatGb(selectedBytes)} / ${formatGb(info.totalBytes)}", width / 2f, height * .94f, min(width, height) * .047f, Color.LTGRAY, Paint.Align.CENTER)
        return bitmap
    }

    fun month(now: ZonedDateTime, weekStart: DayOfWeek, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        val locale = Locale.getDefault()
        val date = now.toLocalDate()
        header(canvas, now.format(DateTimeFormatter.ofPattern("MMM yyyy", locale)).uppercase(locale), width, height)
        val first = date.withDayOfMonth(1)
        val offset = ((first.dayOfWeek.value - weekStart.value) + 7) % 7
        val left = width * .09f
        val right = width * .91f
        val top = height * .28f
        val bottom = height * .90f
        val cellW = (right - left) / 7f
        val cellH = (bottom - top) / 7f
        repeat(7) { col ->
            val day = DayOfWeek.of(((weekStart.value - 1 + col) % 7) + 1)
            text(canvas, day.name.take(1), left + cellW * (col + .5f), top, min(width, height) * .043f, Color.LTGRAY, Paint.Align.CENTER)
        }
        repeat(date.lengthOfMonth()) { zero ->
            val day = zero + 1
            val cell = offset + zero
            val x = left + cellW * (cell % 7 + .5f)
            val y = top + cellH * (cell / 7 + 1.25f)
            val today = day == date.dayOfMonth
            if (today) canvas.drawCircle(x, y - min(width, height) * .014f, min(width, height) * .052f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
            text(canvas, day.toString(), x, y, min(width, height) * .052f, if (today) Color.WHITE else Color.LTGRAY, Paint.Align.CENTER)
        }
        return bitmap
    }

    fun weekStrip(now: ZonedDateTime, weekStart: DayOfWeek, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        header(canvas, "THIS WEEK", width, height)
        val today = now.toLocalDate()
        val delta = ((today.dayOfWeek.value - weekStart.value) + 7) % 7
        val start = today.minusDays(delta.toLong())
        val left = width * .07f
        val cell = width * .86f / 7f
        repeat(7) { i ->
            val date = start.plusDays(i.toLong())
            val x = left + cell * (i + .5f)
            val current = date == today
            text(canvas, date.dayOfWeek.name.take(1), x, height * .39f, min(width, height) * .055f, Color.LTGRAY, Paint.Align.CENTER)
            if (current) canvas.drawCircle(x, height * .66f, min(width, height) * .105f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
            text(canvas, date.dayOfMonth.toString(), x, height * .72f, min(width, height) * .11f, Color.WHITE, Paint.Align.CENTER)
            dot(canvas, x, height * .86f, min(width, height) * .018f, !date.isAfter(today), current)
        }
        return bitmap
    }

    fun year(now: ZonedDateTime, display: YearDisplay, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        val today = now.toLocalDate()
        val totalDays = today.lengthOfYear()
        header(canvas, "YEAR ${now.year}", width, height)
        text(
            canvas,
            if (display == YearDisplay.ELAPSED) "${today.dayOfYear} / $totalDays" else "${totalDays - today.dayOfYear} LEFT",
            width * .93f,
            height * .17f,
            min(width, height) * .055f,
            Color.LTGRAY,
            Paint.Align.RIGHT,
        )
        val labelX = width * .10f
        val left = width * .18f
        val right = width * .94f
        val top = height * .27f
        val bottom = height * .93f
        val colStep = (right - left) / 30f
        val rowStep = (bottom - top) / 11f
        val radius = min(colStep * .24f, rowStep * .18f).coerceAtLeast(1.5f)
        repeat(12) { monthIndex ->
            val first = LocalDate.of(now.year, monthIndex + 1, 1)
            val y = top + monthIndex * rowStep
            text(canvas, first.month.name.take(3), labelX, y + radius * 1.6f, min(width, height) * .035f, Color.LTGRAY, Paint.Align.CENTER)
            repeat(first.lengthOfMonth()) { dayIndex ->
                val date = first.plusDays(dayIndex.toLong())
                val filled = if (display == YearDisplay.ELAPSED) !date.isAfter(today) else !date.isBefore(today)
                dot(canvas, left + dayIndex * colStep, y, radius, filled, date == today)
            }
        }
        return bitmap
    }

    fun devicePanel(
        context: Context,
        now: ZonedDateTime,
        battery: BatteryInfo,
        storage: StorageInfo?,
        alarm: ZonedDateTime?,
        width: Int,
        height: Int,
    ): Bitmap {
        val (bitmap, canvas) = base(width, height)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MID; strokeWidth = 2f }
        canvas.drawLine(width / 2f, height * .12f, width / 2f, height * .88f, line)
        canvas.drawLine(width * .06f, height / 2f, width * .94f, height / 2f, line)
        fun metric(title: String, value: String, detail: String, cx: Float, top: Float, accent: Boolean = false) {
            text(canvas, title, cx, top + height * .10f, min(width, height) * .045f, Color.LTGRAY, Paint.Align.CENTER)
            fittedText(canvas, value, cx, top + height * .25f, width * .39f, min(width, height) * .12f, if (accent) RED else Color.WHITE, Paint.Align.CENTER)
            fittedText(canvas, detail, cx, top + height * .34f, width * .39f, min(width, height) * .038f, Color.LTGRAY, Paint.Align.CENTER)
        }
        metric("BATTERY", battery.percent?.let { "$it%" } ?: "--", if (battery.charging) "CHARGING" else "ON BATTERY", width * .25f, height * .05f, battery.charging)
        metric("STORAGE", storage?.let { "${(it.freeFraction * 100).roundToInt()}%" } ?: "--", "FREE", width * .75f, height * .05f)
        metric("NEXT ALARM", alarm?.let { formatAlarmTime(context, it) } ?: "NONE", alarm?.let { alarmDayLabel(now, it) } ?: "NO ALARM SET", width * .25f, height * .52f, alarm?.toLocalDate() == now.toLocalDate())
        val day = timeProgress(now)[0].fraction
        metric("TODAY", "${(day * 100).toInt()}%", "${now.dayOfWeek.name.take(3)} ${now.dayOfMonth}", width * .75f, height * .52f)
        return bitmap
    }

    fun milestone(now: ZonedDateTime, target: MilestoneTarget, width: Int, height: Int): Bitmap {
        val (bitmap, canvas) = base(width, height)
        header(canvas, target.label.uppercase(Locale.getDefault()), width, height)
        val remaining = milestoneRemaining(now, target)
        fittedText(canvas, remaining?.let(::formatBigDuration) ?: "NOW", width / 2f, height * .61f, width * .86f, min(width, height) * .24f, Color.WHITE, Paint.Align.CENTER)
        val detail = if (remaining == null) "ENJOY IT" else when (target) {
            MilestoneTarget.WEEKEND -> "UNTIL SATURDAY"
            MilestoneTarget.MONTH_END -> "UNTIL NEXT MONTH"
            MilestoneTarget.YEAR_END -> "UNTIL ${now.year + 1}"
        }
        text(canvas, detail, width / 2f, height * .79f, min(width, height) * .06f, if (remaining == null) RED else Color.LTGRAY, Paint.Align.CENTER)
        dottedBar(canvas, milestoneFraction(now, target), width * .12f, width * .88f, height * .91f, if (width > height * 1.5f) 36 else 24)
        return bitmap
    }

    fun clockPreview(context: Context, now: ZonedDateTime, width: Int = 720, height: Int = 320): Bitmap {
        val (bitmap, canvas) = base(width, height)
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        fittedText(canvas, now.format(DateTimeFormatter.ofPattern(pattern)), width * .08f, height * .58f, width * .84f, height * .34f)
        text(canvas, now.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())).uppercase(Locale.getDefault()), width * .09f, height * .82f, height * .10f, Color.LTGRAY)
        dot(canvas, width * .91f, height * .22f, height * .025f, true, true)
        return bitmap
    }

    fun shortcutsPreview(width: Int = 900, height: Int = 300): Bitmap {
        val (bitmap, canvas) = base(width, height)
        header(canvas, "PLAYBOX", width, height)
        val labels = listOf("MATRIX", "WIDGETS", "AOD TOY")
        val gap = width * .025f
        val left = width * .06f
        val right = width * .94f
        val top = height * .31f
        val bottom = height * .84f
        val cellWidth = (right - left - gap * 2) / 3f
        labels.forEachIndexed { index, label ->
            val x = left + index * (cellWidth + gap)
            canvas.drawRoundRect(x, top, x + cellWidth, bottom, height * .08f, height * .08f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = DIM })
            text(canvas, label, x + cellWidth / 2f, top + (bottom - top) * .61f, height * .075f, if (index == 2) RED else Color.WHITE, Paint.Align.CENTER)
        }
        return bitmap
    }

    private fun formatDuration(ms: Long): String {
        val totalMinutes = (ms / 60_000L).coerceAtLeast(1)
        val hours = totalMinutes / 60
        return if (hours > 0) "${hours}H ${totalMinutes % 60}M" else "${totalMinutes}M"
    }

    private fun formatGb(bytes: Long): String = if (bytes >= 100_000_000_000L) {
        "${(bytes / 1_000_000_000.0).roundToInt()} GB"
    } else {
        String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    }

    private fun milestoneRemaining(now: ZonedDateTime, target: MilestoneTarget): Duration? {
        val targetTime = when (target) {
            MilestoneTarget.WEEKEND -> {
                if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) return null
                now.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).atStartOfDay(now.zone)
            }
            MilestoneTarget.MONTH_END -> now.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(now.zone)
            MilestoneTarget.YEAR_END -> LocalDate.of(now.year + 1, 1, 1).atStartOfDay(now.zone)
        }
        return Duration.between(now, targetTime).coerceAtLeast(Duration.ZERO)
    }

    private fun milestoneFraction(now: ZonedDateTime, target: MilestoneTarget): Double = when (target) {
        MilestoneTarget.WEEKEND -> {
            if (now.dayOfWeek == DayOfWeek.SATURDAY || now.dayOfWeek == DayOfWeek.SUNDAY) 1.0 else {
                val start = now.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(now.zone)
                val end = start.plusDays(5)
                (Duration.between(start, now).toMillis().toDouble() / Duration.between(start, end).toMillis()).coerceIn(0.0, 1.0)
            }
        }
        MilestoneTarget.MONTH_END -> {
            val start = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.zone)
            val end = start.plusMonths(1)
            (Duration.between(start, now).toMillis().toDouble() / Duration.between(start, end).toMillis()).coerceIn(0.0, 1.0)
        }
        MilestoneTarget.YEAR_END -> {
            val start = LocalDate.of(now.year, 1, 1).atStartOfDay(now.zone)
            val end = start.plusYears(1)
            (Duration.between(start, now).toMillis().toDouble() / Duration.between(start, end).toMillis()).coerceIn(0.0, 1.0)
        }
    }

    private fun formatBigDuration(duration: Duration): String {
        val minutes = duration.toMinutes().coerceAtLeast(0)
        val days = minutes / (24 * 60)
        val hours = (minutes / 60) % 24
        return when {
            days > 0 -> "${days}D ${hours}H"
            hours > 0 -> "${hours}H ${minutes % 60}M"
            else -> "${minutes}M"
        }
    }
}
