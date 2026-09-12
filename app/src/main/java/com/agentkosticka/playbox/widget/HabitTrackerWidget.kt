package com.agentkosticka.playbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.ui.NothingDotFont
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private const val HABIT_HISTORY_DAYS = 400L

enum class HabitGridStyle { DOTS, SQUARES, RINGS }

data class HabitTrackerState(
    val name: String = "HABIT",
    val completedDays: Set<LocalDate> = emptySet(),
    val style: HabitGridStyle = HabitGridStyle.DOTS,
) {
    val displayName: String get() = name.ifBlank { "HABIT" }

    fun normalized(referenceDate: LocalDate = LocalDate.now()): HabitTrackerState {
        val earliest = referenceDate.minusDays(HABIT_HISTORY_DAYS)
        return copy(
            name = name.take(24),
            completedDays = completedDays.filterTo(linkedSetOf()) {
                !it.isBefore(earliest) && !it.isAfter(referenceDate)
            },
        )
    }

    fun isDone(date: LocalDate): Boolean = date in completedDays

    fun toggle(date: LocalDate, referenceDate: LocalDate = date): HabitTrackerState {
        val state = normalized(referenceDate)
        val updated = state.completedDays.toMutableSet()
        if (!updated.add(date)) updated.remove(date)
        return state.copy(completedDays = updated).normalized(referenceDate)
    }

    fun currentStreak(referenceDate: LocalDate): Int {
        val state = normalized(referenceDate)
        var date = referenceDate
        var streak = 0
        while (date in state.completedDays) {
            streak++
            date = date.minusDays(1)
        }
        return streak
    }
}

class HabitTrackerStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(id: Int?, referenceDate: LocalDate = LocalDate.now()): HabitTrackerState = synchronized(LOCK) {
        if (id == null) decode(preferences.getString(DEFAULTS, null), referenceDate)
        else preferences.getString(key(id), null)?.let { decode(it, referenceDate) }
            ?: load(null, referenceDate).also { put(id, it, referenceDate) }
    }

    fun save(id: Int?, state: HabitTrackerState, referenceDate: LocalDate = LocalDate.now()) = synchronized(LOCK) {
        val normalized = state.normalized(referenceDate)
        if (id == null) {
            snapshotInstalled(referenceDate)
            preferences.edit { putString(DEFAULTS, encode(normalized)) }
        } else {
            put(id, normalized, referenceDate)
            HabitTrackerWidget.update(context, id, referenceDate)
        }
    }

    fun toggleToday(id: Int, today: LocalDate = LocalDate.now()) = synchronized(LOCK) {
        put(id, load(id, today).toggle(today, today), today)
        HabitTrackerWidget.update(context, id, today)
    }

    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove(key(id)) } }

    fun restore(oldIds: IntArray, newIds: IntArray, today: LocalDate = LocalDate.now()) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, value) ->
                if (value == null) remove(key(new)) else putString(key(new), encode(decode(value, today)))
            }
        }
        newIds.forEach { HabitTrackerWidget.update(context, it, today) }
    }

    private fun snapshotInstalled(referenceDate: LocalDate) {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, HabitTrackerWidget::class.java))
            .forEach { id -> if (!preferences.contains(key(id))) put(id, load(null, referenceDate), referenceDate) }
    }

    private fun put(id: Int, state: HabitTrackerState, referenceDate: LocalDate) {
        preferences.edit { putString(key(id), encode(state.normalized(referenceDate))) }
    }

    private fun encode(state: HabitTrackerState): String = JSONObject().apply {
        put("name", state.name)
        put("style", state.style.name)
        put("days", JSONArray().apply { state.completedDays.sorted().forEach { put(it.toString()) } })
    }.toString()

    private fun decode(raw: String?, referenceDate: LocalDate): HabitTrackerState = runCatching {
        if (raw == null) return@runCatching HabitTrackerState()
        val json = JSONObject(raw)
        val days = json.optJSONArray("days") ?: JSONArray()
        HabitTrackerState(
            name = json.optString("name", "HABIT"),
            completedDays = (0 until days.length()).mapNotNull { index ->
                runCatching { LocalDate.parse(days.optString(index)) }.getOrNull()
            }.toSet(),
            style = runCatching { HabitGridStyle.valueOf(json.optString("style", HabitGridStyle.DOTS.name)) }
                .getOrDefault(HabitGridStyle.DOTS),
        ).normalized(referenceDate)
    }.getOrDefault(HabitTrackerState())

    private fun key(id: Int) = "widget.$id"

    private companion object {
        const val PREFS = "habit-tracker"
        const val DEFAULTS = "defaults"
        val LOCK = Any()
    }
}

class HabitTrackerWidget : InstanceWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val today = LocalDate.now()
        val store = HabitTrackerStore(context)
        ids.forEach { render(context, manager, it, store.load(it, today), today) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = HabitTrackerStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        HabitTrackerStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE_TODAY -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id != AppWidgetManager.INVALID_APPWIDGET_ID) HabitTrackerStore(context).toggleToday(id)
                return
            }
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                updateAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE_TODAY = "com.agentkosticka.playbox.action.TOGGLE_HABIT_TODAY"

        fun update(context: Context, id: Int, today: LocalDate = LocalDate.now()) {
            render(context, AppWidgetManager.getInstance(context), id, HabitTrackerStore(context).load(id, today), today)
        }

        private fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, HabitTrackerWidget::class.java))
            if (ids.isNotEmpty()) {
                val today = LocalDate.now()
                val store = HabitTrackerStore(context)
                ids.forEach { render(context, manager, it, store.load(it, today), today) }
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, rawState: HabitTrackerState, today: LocalDate) {
            val state = rawState.normalized(today)
            val streak = state.currentStreak(today)
            val month = YearMonth.from(today)
            val completedThisMonth = state.completedDays.count { YearMonth.from(it) == month }
            val views = RemoteViews(context.packageName, R.layout.widget_habit_tracker)
            views.setTextViewText(R.id.habit_tracker_name, state.displayName.uppercase(Locale.getDefault()))
            views.setTextViewText(
                R.id.habit_tracker_meta,
                "${month.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault())).uppercase(Locale.getDefault())} · $completedThisMonth DAYS",
            )
            views.setTextViewText(R.id.habit_tracker_streak, if (streak == 1) "1 DAY" else "$streak DAYS")
            views.setTextViewText(
                R.id.habit_tracker_toggle,
                context.getString(if (state.isDone(today)) R.string.habit_tracker_done_today else R.string.habit_tracker_mark_today),
            )
            views.setThemedWidgetBitmap(context, R.id.habit_tracker_grid) { palette ->
                HabitTrackerRenderer.monthGrid(state, today, 720, 220, palette)
            }
            views.setOnClickPendingIntent(R.id.habit_tracker_header, widgetPendingIntent(context, WidgetDestination.HABIT_TRACKER, id))
            views.setOnClickPendingIntent(R.id.habit_tracker_toggle, togglePendingIntent(context, id))
            views.setContentDescription(R.id.habit_tracker_root, "${state.displayName}, $completedThisMonth completed days this month, $streak day streak")
            manager.updateAppWidget(id, views)
        }

        private fun togglePendingIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(context, HabitTrackerWidget::class.java)
                .setAction(ACTION_TOGGLE_TODAY)
                .setData(Uri.parse("playbox://habit-tracker/$id/today"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

object HabitTrackerRenderer {
    fun preview(
        rawState: HabitTrackerState,
        today: LocalDate,
        width: Int,
        height: Int,
        palette: WidgetPalette = WidgetPalette.current(),
    ): Bitmap {
        val state = rawState.normalized(today)
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val radius = min(width, height) * .09f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background })
        drawText(canvas, state.displayName.uppercase(Locale.getDefault()), width * .06f, height * .15f,
            min(width, height) * .06f, palette.foreground)
        val streak = state.currentStreak(today)
        drawText(canvas, if (streak == 1) "1 DAY" else "$streak DAYS", width * .94f, height * .15f,
            min(width, height) * .05f, palette.muted, Paint.Align.RIGHT)
        drawMonthGrid(canvas, state, today, width * .06f, height * .22f, width * .94f, height * .77f, palette)
        drawText(canvas, if (state.isDone(today)) "DONE TODAY" else "MARK TODAY", width / 2f, height * .92f,
            min(width, height) * .055f, if (state.isDone(today)) palette.accent else palette.foreground, Paint.Align.CENTER)
        return bitmap
    }

    fun monthGrid(rawState: HabitTrackerState, today: LocalDate, width: Int, height: Int, palette: WidgetPalette = WidgetPalette.current()): Bitmap {
        val state = rawState.normalized(today)
        val bitmap = createBitmap(width, height)
        drawMonthGrid(Canvas(bitmap), state, today, 0f, 0f, width.toFloat(), height.toFloat(), palette)
        return bitmap
    }

    private fun drawMonthGrid(
        canvas: Canvas,
        state: HabitTrackerState,
        today: LocalDate,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        palette: WidgetPalette,
    ) {
        val month = YearMonth.from(today)
        val firstOffset = month.atDay(1).dayOfWeek.value - 1
        val cellWidth = (right - left) / 7f
        val headerHeight = (bottom - top) * .16f
        val rows = (firstOffset + month.lengthOfMonth() + 6) / 7
        val cellHeight = (bottom - top - headerHeight) / rows
        val textSize = min(cellWidth * .25f, headerHeight * .65f)
        val markRadius = min(cellWidth, cellHeight) * .26f
        val labels = arrayOf("M", "T", "W", "T", "F", "S", "S")

        labels.forEachIndexed { index, label ->
            drawText(canvas, label, left + cellWidth * (index + .5f), top + headerHeight * .72f,
                textSize, palette.muted, Paint.Align.CENTER)
        }

        for (day in 1..month.lengthOfMonth()) {
            val index = firstOffset + day - 1
            val column = index % 7
            val row = index / 7
            val date = month.atDay(day)
            val cx = left + cellWidth * (column + .5f)
            val cy = top + headerHeight + cellHeight * (row + .5f)
            val completed = state.isDone(date)
            val future = date.isAfter(today)
            val color = when {
                completed -> palette.accent
                future -> palette.inactive
                else -> palette.muted
            }
            drawMark(canvas, cx, cy, markRadius, color, completed, state.style)
            if (date == today) {
                canvas.drawCircle(cx, cy, markRadius * 1.48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = palette.foreground
                    style = Paint.Style.STROKE
                    strokeWidth = maxOf(2f, markRadius * .28f)
                })
            }
        }
    }

    private fun drawMark(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int, completed: Boolean, style: HabitGridStyle) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        when (style) {
            HabitGridStyle.DOTS -> {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy, radius, paint)
            }
            HabitGridStyle.SQUARES -> {
                paint.style = Paint.Style.FILL
                val corner = radius * .35f
                canvas.drawRoundRect(cx - radius, cy - radius, cx + radius, cy + radius, corner, corner, paint)
            }
            HabitGridStyle.RINGS -> {
                paint.style = if (completed) Paint.Style.FILL else Paint.Style.STROKE
                paint.strokeWidth = maxOf(2f, radius * .36f)
                canvas.drawCircle(cx, cy, radius, paint)
            }
        }
    }

    private fun drawText(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
        canvas.drawText(value, x, baseline, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = WidgetTypography.label
            textAlign = align
            isSubpixelText = true
        })
    }
}
