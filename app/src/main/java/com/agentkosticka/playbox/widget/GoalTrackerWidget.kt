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
            val views = RemoteViews(context.packageName, R.layout.widget_goal_tracker)
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
                GoalTrackerRenderer.render(state, label, unit, 720, 300, palette)
            }
            views.setOnClickPendingIntent(R.id.goal_minus, action(context, id, ACTION_MINUS, 1))
            views.setOnClickPendingIntent(R.id.goal_plus, action(context, id, ACTION_PLUS, 2))
            views.setOnClickPendingIntent(R.id.goal_mode, action(context, id, ACTION_MODE, 3))
            views.setOnClickPendingIntent(R.id.goal_look, action(context, id, ACTION_LOOK, 4))
            views.setContentDescription(
                R.id.goal_root,
                context.getString(R.string.goal_content_description, label, state.value, state.target, unit, state.streak()),
            )
            manager.updateAppWidget(id, views)
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
        val dot = NothingDotFont.typeface
        val pad = width * .055f
        val progress = (state.value.toFloat() / state.target.coerceAtLeast(1)).coerceIn(0f, 1f)

        canvas.drawText(label.uppercase(Locale.getDefault()), pad, height * .18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = min(width, height) * .066f
            typeface = dot
        })
        canvas.drawText("${state.value}/${state.target} ${unit.uppercase(Locale.getDefault())}", width - pad, height * .18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = min(width, height) * .045f
            typeface = dot
            textAlign = Paint.Align.RIGHT
        })

        when (state.visual) {
            GoalVisual.RING -> {
                val cx = width * .50f
                val cy = height * .52f
                val r = min(width, height) * .22f
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawArc(rect, -90f, 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.inactive
                    style = Paint.Style.STROKE
                    strokeWidth = r * .22f
                    strokeCap = Paint.Cap.ROUND
                })
                canvas.drawArc(rect, -90f, progress * 360f, false, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.accent
                    style = Paint.Style.STROKE
                    strokeWidth = r * .22f
                    strokeCap = Paint.Cap.ROUND
                })
                canvas.drawText("${(progress * 100).toInt()}%", cx, cy + min(width, height) * .045f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = palette.foreground
                    textSize = min(width, height) * .105f
                    typeface = dot
                    textAlign = Paint.Align.CENTER
                })
            }
            GoalVisual.BAR -> {
                val left = width * .10f
                val right = width * .90f
                val top = height * .46f
                val bottom = height * .60f
                canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.inactive })
                canvas.drawRoundRect(left, top, left + (right - left) * progress, bottom, 28f, 28f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent })
            }
            GoalVisual.DOTS -> {
                val dots = state.target.coerceIn(5, 30)
                val active = (progress * dots).toInt().coerceIn(0, dots)
                val columns = min(dots, 10)
                val rows = (dots + columns - 1) / columns
                val dotRadius = min(width / (columns * 3f), height / (rows * 5f)).coerceAtLeast(6f)
                repeat(dots) { index ->
                    val col = index % columns
                    val row = index / columns
                    val x = width * .18f + (width * .64f) * (col + .5f) / columns
                    val y = height * .38f + (height * .30f) * (row + .5f) / rows
                    canvas.drawCircle(x, y, dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (index < active) palette.accent else palette.inactive
                    })
                }
            }
        }

        val streak = state.streak()
        canvas.drawText("$streak DAY STREAK", pad, height * .88f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = min(width, height) * .043f
            typeface = dot
        })
        return bitmap
    }
}
