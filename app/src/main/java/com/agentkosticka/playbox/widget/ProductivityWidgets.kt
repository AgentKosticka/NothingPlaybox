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
import android.text.format.DateFormat
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.ui.NothingDotFont
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private const val QUICK_TASK_COUNT = 4

data class QuickTask(val text: String = "", val done: Boolean = false)

data class QuickTasksState(
    val title: String = "TODAY",
    val tasks: List<QuickTask> = List(QUICK_TASK_COUNT) { QuickTask() },
) {
    fun normalized(): QuickTasksState = copy(
        title = title.take(24).ifBlank { "TODAY" },
        tasks = tasks.take(QUICK_TASK_COUNT).map { task ->
            QuickTask(task.text.take(60), task.done && task.text.isNotBlank())
        }.let { it + List((QUICK_TASK_COUNT - it.size).coerceAtLeast(0)) { QuickTask() } },
    )

    fun withDone(index: Int, done: Boolean): QuickTasksState {
        if (index !in 0 until QUICK_TASK_COUNT) return this
        val normalized = normalized()
        if (normalized.tasks[index].text.isBlank()) return normalized
        val updated = normalized.tasks.toMutableList()
        updated[index] = updated[index].copy(done = done)
        return normalized.copy(tasks = updated)
    }
}

class QuickTasksStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(id: Int?): QuickTasksState = synchronized(LOCK) {
        if (id == null) decode(preferences.getString(DEFAULTS, null))
        else preferences.getString(key(id), null)?.let(::decode) ?: load(null).also { put(id, it) }
    }

    fun save(id: Int?, state: QuickTasksState) = synchronized(LOCK) {
        val normalized = state.normalized()
        if (id == null) {
            snapshotInstalled()
            preferences.edit { putString(DEFAULTS, encode(normalized)) }
        } else {
            put(id, normalized)
            QuickTasksWidget.update(context, id)
        }
    }

    fun setDone(id: Int, index: Int, done: Boolean) = synchronized(LOCK) {
        put(id, load(id).withDone(index, done))
        QuickTasksWidget.update(context, id)
    }

    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove(key(id)) } }

    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, value) ->
                if (value == null) remove(key(new)) else putString(key(new), value)
            }
        }
        newIds.forEach { QuickTasksWidget.update(context, it) }
    }

    private fun snapshotInstalled() {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, QuickTasksWidget::class.java))
            .forEach { id -> if (!preferences.contains(key(id))) put(id, load(null)) }
    }

    private fun put(id: Int, state: QuickTasksState) {
        preferences.edit { putString(key(id), encode(state)) }
    }

    private fun encode(state: QuickTasksState): String = JSONObject().apply {
        val normalized = state.normalized()
        put("title", normalized.title)
        put("tasks", JSONArray().apply {
            normalized.tasks.forEach { task ->
                put(JSONObject().put("text", task.text).put("done", task.done))
            }
        })
    }.toString()

    private fun decode(raw: String?): QuickTasksState = runCatching {
        if (raw == null) return@runCatching QuickTasksState()
        val json = JSONObject(raw)
        val array = json.optJSONArray("tasks") ?: JSONArray()
        QuickTasksState(
            title = json.optString("title", "TODAY"),
            tasks = (0 until min(array.length(), QUICK_TASK_COUNT)).map { index ->
                val task = array.optJSONObject(index) ?: JSONObject()
                QuickTask(task.optString("text"), task.optBoolean("done"))
            },
        ).normalized()
    }.getOrDefault(QuickTasksState())

    private fun key(id: Int) = "widget.$id"

    private companion object {
        const val PREFS = "quick-tasks"
        const val DEFAULTS = "defaults"
        val LOCK = Any()
    }
}

class QuickTasksWidget : InstanceWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = QuickTasksStore(context)
        ids.forEach { render(context, manager, it, store.load(it)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = QuickTasksStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        QuickTasksStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_SET_DONE) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val index = intent.getIntExtra(EXTRA_TASK_INDEX, -1)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID || index !in 0 until QUICK_TASK_COUNT) return
        QuickTasksStore(context).setDone(id, index, intent.getBooleanExtra(RemoteViews.EXTRA_CHECKED, false))
    }

    companion object {
        private const val ACTION_SET_DONE = "com.agentkosticka.playbox.action.SET_QUICK_TASK_DONE"
        private const val EXTRA_TASK_INDEX = "quick_task_index"

        private val taskViews = intArrayOf(
            R.id.quick_task_1,
            R.id.quick_task_2,
            R.id.quick_task_3,
            R.id.quick_task_4,
        )

        fun update(context: Context, id: Int) {
            render(context, AppWidgetManager.getInstance(context), id, QuickTasksStore(context).load(id))
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, rawState: QuickTasksState) {
            val state = rawState.normalized()
            val views = RemoteViews(context.packageName, R.layout.widget_quick_tasks)
            val visibleTasks = state.tasks.count { it.text.isNotBlank() }
            val doneTasks = state.tasks.count { it.text.isNotBlank() && it.done }
            views.setTextViewText(R.id.quick_tasks_title, state.title)
            views.setTextViewText(R.id.quick_tasks_progress, "$doneTasks/$visibleTasks")
            views.setOnClickPendingIntent(
                R.id.quick_tasks_header,
                widgetPendingIntent(context, WidgetDestination.QUICK_TASKS, id),
            )
            views.setOnClickPendingIntent(
                R.id.quick_tasks_empty,
                widgetPendingIntent(context, WidgetDestination.QUICK_TASKS, id),
            )
            views.setViewVisibility(R.id.quick_tasks_empty, if (visibleTasks == 0) View.VISIBLE else View.GONE)

            state.tasks.forEachIndexed { index, task ->
                val viewId = taskViews[index]
                val visible = task.text.isNotBlank()
                views.setViewVisibility(viewId, if (visible) View.VISIBLE else View.GONE)
                if (visible) {
                    views.setTextViewText(viewId, task.text)
                    views.setCompoundButtonChecked(viewId, task.done)
                    views.setOnCheckedChangeResponse(
                        viewId,
                        RemoteViews.RemoteResponse.fromPendingIntent(toggleIntent(context, id, index)),
                    )
                }
            }
            views.setContentDescription(
                R.id.quick_tasks_root,
                if (visibleTasks == 0) "Quick Tasks, empty" else "Quick Tasks, $doneTasks of $visibleTasks complete",
            )
            manager.updateAppWidget(id, views)
        }

        private fun toggleIntent(context: Context, id: Int, index: Int): PendingIntent {
            val intent = Intent(context, QuickTasksWidget::class.java)
                .setAction(ACTION_SET_DONE)
                .setData(Uri.parse("playbox://quick-tasks/$id/$index"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .putExtra(EXTRA_TASK_INDEX, index)
            return PendingIntent.getBroadcast(
                context,
                index,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

internal fun validatedZoneId(value: String?): String =
    runCatching { ZoneId.of(value ?: "UTC").id }.getOrDefault("UTC")

object DualClockZones {
    data class Option(val id: String, val label: String)

    val common = listOf(
        Option("UTC", "UTC"),
        Option("America/Los_Angeles", "Los Angeles"),
        Option("America/New_York", "New York"),
        Option("America/Sao_Paulo", "São Paulo"),
        Option("Europe/London", "London"),
        Option("Europe/Prague", "Prague"),
        Option("Europe/Berlin", "Berlin"),
        Option("Asia/Dubai", "Dubai"),
        Option("Asia/Kolkata", "Kolkata"),
        Option("Asia/Singapore", "Singapore"),
        Option("Asia/Tokyo", "Tokyo"),
        Option("Australia/Sydney", "Sydney"),
        Option("Pacific/Auckland", "Auckland"),
    )

    fun label(zoneId: String): String = common.firstOrNull { it.id == validatedZoneId(zoneId) }?.label
        ?: validatedZoneId(zoneId).substringAfterLast('/').replace('_', ' ')
}

class DualClockStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(id: Int?): String = synchronized(LOCK) {
        if (id == null) validatedZoneId(preferences.getString(DEFAULTS, "UTC"))
        else preferences.getString(key(id), null)?.let(::validatedZoneId) ?: load(null).also { put(id, it) }
    }

    fun save(id: Int?, zoneId: String) = synchronized(LOCK) {
        val zone = validatedZoneId(zoneId)
        if (id == null) {
            snapshotInstalled()
            preferences.edit { putString(DEFAULTS, zone) }
        } else {
            put(id, zone)
            DualClockWidget.update(context, id)
        }
    }

    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove(key(id)) } }

    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, value) ->
                if (value == null) remove(key(new)) else putString(key(new), validatedZoneId(value))
            }
        }
        newIds.forEach { DualClockWidget.update(context, it) }
    }

    private fun snapshotInstalled() {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, DualClockWidget::class.java))
            .forEach { id -> if (!preferences.contains(key(id))) put(id, load(null)) }
    }

    private fun put(id: Int, zoneId: String) {
        preferences.edit { putString(key(id), validatedZoneId(zoneId)) }
    }

    private fun key(id: Int) = "widget.$id"

    private companion object {
        const val PREFS = "dual-clock"
        const val DEFAULTS = "defaults"
        val LOCK = Any()
    }
}

class DualClockWidget : InstanceWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = DualClockStore(context)
        ids.forEach { render(context, manager, it, store.load(it)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = DualClockStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        DualClockStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    companion object {
        fun update(context: Context, id: Int) {
            render(context, AppWidgetManager.getInstance(context), id, DualClockStore(context).load(id))
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, rawZoneId: String) {
            val zoneId = validatedZoneId(rawZoneId)
            val views = RemoteViews(context.packageName, R.layout.widget_dual_clock)
            views.setTextViewText(R.id.dual_clock_remote_label, DualClockZones.label(zoneId).uppercase(Locale.getDefault()))
            views.setString(R.id.dual_clock_remote_time, "setTimeZone", zoneId)
            views.setString(R.id.dual_clock_remote_date, "setTimeZone", zoneId)
            views.setOnClickPendingIntent(
                R.id.dual_clock_root,
                widgetPendingIntent(context, WidgetDestination.DUAL_CLOCK, id),
            )
            views.setContentDescription(
                R.id.dual_clock_root,
                "Local time and ${DualClockZones.label(zoneId)} time",
            )
            manager.updateAppWidget(id, views)
        }
    }
}

object ProductivityWidgetRenderer {
    fun quickTasks(
        rawState: QuickTasksState,
        width: Int,
        height: Int,
        palette: WidgetPalette = WidgetPalette.current(),
    ): Bitmap {
        val state = rawState.normalized()
        val (bitmap, canvas) = base(width, height, palette)
        val active = state.tasks.filter { it.text.isNotBlank() }
        val done = active.count { it.done }
        text(canvas, state.title.uppercase(Locale.getDefault()), width * .06f, height * .16f,
            min(width, height) * .065f, palette.foreground)
        text(canvas, "$done/${active.size}", width * .94f, height * .16f,
            min(width, height) * .055f, palette.muted, Paint.Align.RIGHT)
        if (active.isEmpty()) {
            text(canvas, "TAP TO ADD TASKS", width / 2f, height * .57f,
                min(width, height) * .075f, palette.muted, Paint.Align.CENTER)
            return bitmap
        }
        active.take(QUICK_TASK_COUNT).forEachIndexed { index, task ->
            val y = height * (.32f + index * .17f)
            val radius = min(width, height) * .018f
            canvas.drawCircle(width * .075f, y - radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (task.done) palette.accent else palette.inactive
                style = if (task.done) Paint.Style.FILL else Paint.Style.STROKE
                strokeWidth = radius * .45f
            })
            fittedText(canvas, task.text, width * .12f, y, width * .80f,
                min(width, height) * .06f, if (task.done) palette.muted else palette.foreground)
        }
        return bitmap
    }

    fun dualClock(
        context: Context,
        now: ZonedDateTime,
        rawZoneId: String,
        width: Int,
        height: Int,
        palette: WidgetPalette = WidgetPalette.current(),
    ): Bitmap {
        val zoneId = validatedZoneId(rawZoneId)
        val remote = now.withZoneSameInstant(ZoneId.of(zoneId))
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
        val timeFormat = DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
        val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        val (bitmap, canvas) = base(width, height, palette)
        val mid = width / 2f
        text(canvas, "LOCAL", width * .06f, height * .20f, min(width, height) * .055f, palette.muted)
        fittedText(canvas, now.format(timeFormat), width * .06f, height * .55f, width * .39f,
            min(width, height) * .19f, palette.foreground)
        fittedText(canvas, now.format(dateFormat).uppercase(Locale.getDefault()), width * .06f, height * .75f, width * .39f,
            min(width, height) * .05f, palette.muted)
        canvas.drawRect(mid - 1f, height * .16f, mid + 1f, height * .82f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.inactive })
        text(canvas, DualClockZones.label(zoneId).uppercase(Locale.getDefault()), width * .55f, height * .20f,
            min(width, height) * .055f, palette.muted)
        fittedText(canvas, remote.format(timeFormat), width * .55f, height * .55f, width * .39f,
            min(width, height) * .19f, palette.foreground)
        fittedText(canvas, remote.format(dateFormat).uppercase(Locale.getDefault()), width * .55f, height * .75f, width * .39f,
            min(width, height) * .05f, palette.accent)
        return bitmap
    }

    private fun base(width: Int, height: Int, palette: WidgetPalette): Pair<Bitmap, Canvas> {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val radius = min(width, height) * .09f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background })
        return bitmap to canvas
    }

    private fun text(
        canvas: Canvas,
        value: String,
        x: Float,
        baseline: Float,
        size: Float,
        color: Int,
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
        color: Int,
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
}
