package com.agentkosticka.playbox.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.ui.NothingDotFont
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.Locale
import kotlin.math.min

enum class GoalPreset {
    WATER,
    READ,
    MOVE,
    CUSTOM;

    fun next(): GoalPreset = entries[(ordinal + 1) % entries.size]

    val target: Int
        get() = when (this) {
            WATER -> 8
            READ, MOVE -> 30
            CUSTOM -> 10
        }

    val step: Int
        get() = when (this) {
            WATER, CUSTOM -> 1
            READ, MOVE -> 5
        }
}

enum class GoalVisual {
    RING,
    BAR,
    DOTS;

    fun next(): GoalVisual = entries[(ordinal + 1) % entries.size]
}

data class GoalTrackerState(
    val preset: GoalPreset = GoalPreset.WATER,
    val value: Int = 0,
    val dayEpoch: Long = LocalDate.now().toEpochDay(),
    val history: Map<Long, Int> = emptyMap(),
    val visual: GoalVisual = GoalVisual.RING,
    val style: WidgetVisualStyle = WidgetVisualStyle.DYNAMIC,
) {
    val target: Int get() = preset.target
    val step: Int get() = preset.step

    fun normalized(today: Long = LocalDate.now().toEpochDay()): GoalTrackerState {
        val boundedHistory = history
            .filterKeys { it in (today - 90)..today }
            .mapValues { (_, amount) -> amount.coerceIn(0, 100_000) }
        return copy(
            value = value.coerceIn(0, 100_000),
            dayEpoch = dayEpoch.coerceAtMost(today),
            history = boundedHistory,
        )
    }

    fun rolledTo(today: Long): GoalTrackerState {
        val normalized = normalized(today)
        if (normalized.dayEpoch == today) return normalized
        val history = normalized.history.toMutableMap()
        if (normalized.dayEpoch > 0) history[normalized.dayEpoch] = normalized.value
        return normalized.copy(value = 0, dayEpoch = today, history = history).normalized(today)
    }

    fun streak(today: Long = LocalDate.now().toEpochDay()): Int {
        val state = rolledTo(today)
        var day = if (state.value >= state.target) today else today - 1
        var count = 0
        while (day >= today - 90) {
            val amount = if (day == today) state.value else state.history[day] ?: break
            if (amount < state.target) break
            count++
            day--
        }
        return count
    }
}

class GoalTrackerStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(id: Int?): GoalTrackerState = synchronized(LOCK) {
        val today = LocalDate.now().toEpochDay()
        val raw = if (id == null) decode(preferences.getString(DEFAULTS, null))
        else preferences.getString(key(id), null)?.let(::decode) ?: load(null).also { put(id, it) }
        val rolled = raw.rolledTo(today)
        if (id != null && rolled != raw) put(id, rolled)
        rolled
    }

    fun configure(id: Int?, preset: GoalPreset, visual: GoalVisual, style: WidgetVisualStyle) = synchronized(LOCK) {
        fun configured(current: GoalTrackerState): GoalTrackerState = current.copy(
            preset = preset, visual = visual, style = style,
            value = if (preset == current.preset) current.value else 0,
            history = if (preset == current.preset) current.history else emptyMap(),
        )
        if (id == null) {
            AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, GoalTrackerWidget::class.java)).forEach { load(it) }
            preferences.edit { putString(DEFAULTS, encode(configured(load(null)))) }
        } else mutate(id, ::configured)
    }

    fun change(id: Int, direction: Int) = mutate(id) { state ->
        state.copy(value = (state.value + state.step * direction).coerceAtLeast(0))
    }

    fun cyclePreset(id: Int) = mutate(id) { state ->
        state.copy(preset = state.preset.next(), value = 0, history = emptyMap())
    }

    fun cycleLook(id: Int) = mutate(id) { state ->
        val nextVisual = state.visual.next()
        state.copy(
            visual = nextVisual,
            style = if (nextVisual == GoalVisual.RING) state.style.next() else state.style,
        )
    }

    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove(key(id)) } }

    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, value) -> if (value == null) remove(key(new)) else putString(key(new), value) }
        }
        newIds.forEach { GoalTrackerWidget.update(context, it) }
    }

    private inline fun mutate(id: Int, transform: (GoalTrackerState) -> GoalTrackerState) = synchronized(LOCK) {
        val next = transform(load(id)).rolledTo(LocalDate.now().toEpochDay())
        put(id, next)
        GoalTrackerWidget.update(context, id)
    }

    private fun put(id: Int, state: GoalTrackerState) {
        preferences.edit { putString(key(id), encode(state.normalized())) }
    }

    private fun key(id: Int) = "widget.$id"

    private fun encode(state: GoalTrackerState): String = JSONObject().apply {
        put("preset", state.preset.name)
        put("value", state.value)
        put("dayEpoch", state.dayEpoch)
        put("visual", state.visual.name)
        put("style", state.style.name)
        put("history", JSONArray().apply {
            state.history.toSortedMap().forEach { (day, value) ->
                put(JSONObject().put("day", day).put("value", value))
            }
        })
    }.toString()

    private fun decode(raw: String?): GoalTrackerState = runCatching {
        if (raw == null) return@runCatching GoalTrackerState()
        val json = JSONObject(raw)
        val history = mutableMapOf<Long, Int>()
        val array = json.optJSONArray("history") ?: JSONArray()
        repeat(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@repeat
            history[item.optLong("day")] = item.optInt("value")
        }
        GoalTrackerState(
            preset = runCatching { GoalPreset.valueOf(json.optString("preset", GoalPreset.WATER.name)) }
                .getOrDefault(GoalPreset.WATER),
            value = json.optInt("value", 0),
            dayEpoch = json.optLong("dayEpoch", LocalDate.now().toEpochDay()),
            history = history,
            visual = runCatching { GoalVisual.valueOf(json.optString("visual", GoalVisual.RING.name)) }
                .getOrDefault(GoalVisual.RING),
            style = runCatching { WidgetVisualStyle.valueOf(json.optString("style", WidgetVisualStyle.DYNAMIC.name)) }
                .getOrDefault(WidgetVisualStyle.DYNAMIC),
        ).rolledTo(LocalDate.now().toEpochDay())
    }.getOrDefault(GoalTrackerState())

    private companion object {
        const val PREFS = "goal-tracker"
        const val DEFAULTS = "defaults"
        val LOCK = Any()
    }
}

class GoalTrackerWidget : InstanceWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = GoalTrackerStore(context)
        ids.forEach { render(context, manager, it, store.load(it)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = GoalTrackerStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        GoalTrackerStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_MINUS, ACTION_PLUS, ACTION_MODE, ACTION_LOOK -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val store = GoalTrackerStore(context)
                when (intent.action) {
                    ACTION_MINUS -> store.change(id, -1)
                    ACTION_PLUS -> store.change(id, 1)
                    ACTION_MODE -> store.cyclePreset(id)
                    ACTION_LOOK -> store.cycleLook(id)
                }
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
        private const val ACTION_MINUS = "com.agentkosticka.playbox.action.GOAL_MINUS"
        private const val ACTION_PLUS = "com.agentkosticka.playbox.action.GOAL_PLUS"
        private const val ACTION_MODE = "com.agentkosticka.playbox.action.GOAL_MODE"
        private const val ACTION_LOOK = "com.agentkosticka.playbox.action.GOAL_LOOK"

        fun update(context: Context, id: Int) =
            render(context, AppWidgetManager.getInstance(context), id, GoalTrackerStore(context).load(id))

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val store = GoalTrackerStore(context)
            manager.getAppWidgetIds(ComponentName(context, GoalTrackerWidget::class.java))
                .forEach { id -> render(context, manager, id, store.load(id)) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, state: GoalTrackerState) {
            val sizedViews = interactiveWidgetViews(manager.getAppWidgetOptions(id), 56) { width, height ->
                val views = RemoteViews(context.packageName, R.layout.widget_goal_tracker)
                views.setWidgetSurface(context, R.id.goal_root, state.style)
                val label = context.getString(
                    when (state.preset) {
                        GoalPreset.WATER -> R.string.goal_water
                        GoalPreset.READ -> R.string.goal_read
                        GoalPreset.MOVE -> R.string.goal_move
                        GoalPreset.CUSTOM -> R.string.goal_custom
                    },
                )
                val unit = context.getString(
                    when (state.preset) {
                        GoalPreset.WATER -> R.string.goal_unit_glasses
                        GoalPreset.READ, GoalPreset.MOVE -> R.string.goal_unit_minutes
                        GoalPreset.CUSTOM -> R.string.goal_unit_units
                    },
                )
                views.setStyledWidgetBitmap(context, R.id.goal_background, state.style) { palette ->
                    GoalTrackerRenderer.render(state, label, unit, width, height, palette.copy(background = android.graphics.Color.TRANSPARENT))
                }
                views.setOnClickPendingIntent(R.id.goal_background, widgetPendingIntent(context, WidgetDestination.GOAL_TRACKER, id))
                views.setOnClickPendingIntent(R.id.goal_minus, action(context, id, ACTION_MINUS, 1))
                views.setOnClickPendingIntent(R.id.goal_plus, action(context, id, ACTION_PLUS, 2))


                views.setContentDescription(
                    R.id.goal_root,
                    context.getString(R.string.goal_content_description, label, state.value, state.target, unit, state.streak()),
                )
                views
            }
            manager.updateAppWidget(id, sizedViews)
        }

        private fun action(context: Context, id: Int, action: String, suffix: Int): PendingIntent {
            val intent = Intent(context, GoalTrackerWidget::class.java)
                .setAction(action)
                .setData(Uri.parse("playbox://goal/$id/$suffix"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context,
                id * 10 + suffix,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

object GoalTrackerRenderer {
    fun render(
        state: GoalTrackerState,
        label: String,
        unit: String,
        width: Int,
        height: Int,
        palette: WidgetPalette,
    ): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val radius = min(width, height) * .09f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.background
        })
        val scale = min(width / 280f, height / 104f)
        val pad = 16f * scale
        val progress = (state.value.toFloat() / state.target.coerceAtLeast(1)).coerceIn(0f, 1f)
        val cy = height * .53f
        val cx = width - pad - 34f * scale
        val r = 29f * scale
        WidgetTypography.text(canvas, label.uppercase(Locale.getDefault()), pad, 22f * scale,
            12f * scale, palette.muted, width * .58f, face = WidgetTypography.label)
        WidgetTypography.number(canvas, state.value.toString(), pad, height * .70f,
            48f * scale, palette.foreground, width * .56f, face = NothingDotFont.typeface)
        WidgetTypography.text(canvas, "of ${state.target} $unit", pad, height - 8f * scale,
            12f * scale, palette.muted, width * .58f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (state.visual) {
            GoalVisual.RING -> {
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 5f * scale
                paint.strokeCap = Paint.Cap.ROUND
                paint.color = palette.inactive
                canvas.drawArc(rect, -90f, 360f, false, paint)
                paint.color = palette.accent
                if (progress > 0f) canvas.drawArc(rect, -90f, progress * 360f, false, paint)
                WidgetTypography.text(canvas, "${(progress * 100).toInt()}%", cx, cy + 4f * scale,
                    12f * scale, palette.foreground, r * 1.8f, Paint.Align.CENTER)
            }
            GoalVisual.BAR -> {
                repeat(10) { index ->
                    paint.color = if (index < (progress * 10).toInt()) palette.accent else palette.inactive
                    val y = cy + r - index * 6f * scale
                    canvas.drawRoundRect(cx - r, y - 4f * scale, cx + r, y, 2f * scale, 2f * scale, paint)
                }
            }
            GoalVisual.DOTS -> {
                val count = state.target.coerceAtMost(30)
                val columns = if (count <= 10) 4 else 5
                val rows = (count + columns - 1) / columns
                val pitch = min(14f * scale, r * 2 / rows)
                repeat(count) { index ->
                    paint.color = if (index < state.value) palette.accent else palette.inactive
                    canvas.drawCircle(cx + (index % columns - (columns - 1) / 2f) * pitch,
                        cy + (index / columns - (rows - 1) / 2f) * pitch, pitch * .28f, paint)
                }
            }
        }
        WidgetTypography.text(canvas, "${state.streak()} day streak", cx, height - 8f * scale,
            10f * scale, palette.muted, width * .34f, Paint.Align.CENTER)
        return bitmap
    }
}
