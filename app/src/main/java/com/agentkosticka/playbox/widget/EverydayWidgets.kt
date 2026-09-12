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
import org.json.JSONObject
import java.util.Locale
import kotlin.math.min

enum class TallyStyle { BIG_NUMBER, DOT_MATRIX }

data class TallyState(
    val label: String = "COUNT",
    val value: Int = 0,
    val step: Int = 1,
    val style: TallyStyle = TallyStyle.BIG_NUMBER,
) {
    fun normalized(): TallyState = copy(
        label = label.take(24),
        value = value.coerceIn(-999_999, 999_999),
        step = step.coerceIn(1, 1000),
    )
}

class TallyStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(id: Int?): TallyState = synchronized(LOCK) {
        if (id == null) decode(preferences.getString(DEFAULTS, null))
        else preferences.getString(key(id), null)?.let(::decode) ?: load(null).also { put(id, it) }
    }

    fun save(id: Int?, state: TallyState) = synchronized(LOCK) {
        val normalized = state.normalized()
        if (id == null) {
            snapshotInstalled()
            preferences.edit { putString(DEFAULTS, encode(normalized)) }
        } else {
            put(id, normalized)
            TallyCounterWidget.update(context, id)
        }
    }

    fun change(id: Int, direction: Int) = synchronized(LOCK) {
        val state = load(id)
        save(id, state.copy(value = (state.value.toLong() + state.step.toLong() * direction).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()))
    }

    fun reset(id: Int) = synchronized(LOCK) { save(id, load(id).copy(value = 0)) }
    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove(key(id)) } }

    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, value) -> if (value == null) remove(key(new)) else putString(key(new), value) }
        }
        newIds.forEach { TallyCounterWidget.update(context, it) }
    }

    private fun snapshotInstalled() {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, TallyCounterWidget::class.java))
            .forEach { id -> if (!preferences.contains(key(id))) put(id, load(null)) }
    }

    private fun put(id: Int, state: TallyState) { preferences.edit { putString(key(id), encode(state.normalized())) } }
    private fun key(id: Int) = "widget.$id"
    private fun encode(state: TallyState) = JSONObject()
        .put("label", state.label).put("value", state.value).put("step", state.step).put("style", state.style.name).toString()
    private fun decode(raw: String?): TallyState = runCatching {
        if (raw == null) return@runCatching TallyState()
        val json = JSONObject(raw)
        TallyState(
            label = json.optString("label", "COUNT"),
            value = json.optInt("value", 0),
            step = json.optInt("step", 1),
            style = runCatching { TallyStyle.valueOf(json.optString("style")) }.getOrDefault(TallyStyle.BIG_NUMBER),
        ).normalized()
    }.getOrDefault(TallyState())

    private companion object { const val PREFS = "tally-counter"; const val DEFAULTS = "defaults"; val LOCK = Any() }
}

class TallyCounterWidget : InstanceWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = TallyStore(context)
        ids.forEach { render(context, manager, it, store.load(it)) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = TallyStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        TallyStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) when (intent.action) {
            ACTION_MINUS -> TallyStore(context).change(id, -1)
            ACTION_PLUS -> TallyStore(context).change(id, 1)
            ACTION_RESET -> TallyStore(context).reset(id)
            else -> { super.onReceive(context, intent); return }
        } else super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_MINUS = "com.agentkosticka.playbox.action.TALLY_MINUS"
        private const val ACTION_PLUS = "com.agentkosticka.playbox.action.TALLY_PLUS"
        private const val ACTION_RESET = "com.agentkosticka.playbox.action.TALLY_RESET"

        fun update(context: Context, id: Int) = render(context, AppWidgetManager.getInstance(context), id, TallyStore(context).load(id))

        private fun render(context: Context, manager: AppWidgetManager, id: Int, state: TallyState) {
            val sizedViews = interactiveWidgetViews(manager.getAppWidgetOptions(id), 56) { width, height ->
                val views = RemoteViews(context.packageName, R.layout.widget_tally_counter)
                views.setThemedWidgetBitmap(context, R.id.tally_image) { palette -> TallyRenderer.render(state, width, height, palette) }
                views.setOnClickPendingIntent(R.id.tally_image, widgetPendingIntent(context, WidgetDestination.TALLY_COUNTER, id))
                views.setOnClickPendingIntent(R.id.tally_minus, action(context, id, ACTION_MINUS, "minus"))
                views.setOnClickPendingIntent(R.id.tally_reset, action(context, id, ACTION_RESET, "reset"))
                views.setOnClickPendingIntent(R.id.tally_plus, action(context, id, ACTION_PLUS, "plus"))
                views.setContentDescription(R.id.tally_root, "${state.label.ifBlank { "Counter" }} ${state.value}, step ${state.step}")
                views
            }
            manager.updateAppWidget(id, sizedViews)
        }

        private fun action(context: Context, id: Int, action: String, suffix: String): PendingIntent {
            val intent = Intent(context, TallyCounterWidget::class.java)
                .setAction(action)
                .setData(Uri.parse("playbox://tally/$id/$suffix"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(context, id * 10 + suffix.hashCode().and(7), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}

object TallyRenderer {
    fun render(rawState: TallyState, width: Int, height: Int, palette: WidgetPalette = WidgetPalette.current()): Bitmap {
        val state = rawState.normalized()
        val bitmap = card(width, height, palette)
        val canvas = Canvas(bitmap)
        drawText(canvas, state.label.ifBlank { "COUNT" }.uppercase(Locale.getDefault()), width * .06f, height * .17f,
            min(width / 280f, height / 104f) * 12f, palette.muted)
        val value = state.value.toString()
        val size = min(height * .48f, width * .84f / maxOf(1, value.length) * 1.3f)
        WidgetTypography.number(canvas, value, width / 2f, height * .76f, size, palette.foreground,
            width * .88f, Paint.Align.CENTER,
            if (state.style == TallyStyle.DOT_MATRIX) NothingDotFont.typeface else android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL))
        return bitmap
    }
}

enum class PinnedNoteStyle { CARD, TERMINAL, MINIMAL }

data class PinnedNoteState(
    val title: String = "NOTE",
    val text: String = "",
    val style: PinnedNoteStyle = PinnedNoteStyle.CARD,
) {
    fun normalized() = copy(title = title.take(28), text = text.take(500))
}

class PinnedNoteStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun load(id: Int?): PinnedNoteState = synchronized(LOCK) {
        if (id == null) decode(preferences.getString(DEFAULTS, null))
        else preferences.getString(key(id), null)?.let(::decode) ?: load(null).also { put(id, it) }
    }
    fun save(id: Int?, state: PinnedNoteState) = synchronized(LOCK) {
        val normalized = state.normalized()
        if (id == null) { snapshotInstalled(); preferences.edit { putString(DEFAULTS, encode(normalized)) } }
        else { put(id, normalized); PinnedNoteWidget.update(context, id) }
    }
    fun delete(id: Int) = synchronized(LOCK) { preferences.edit { remove(key(id)) } }
    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, value) -> if (value == null) remove(key(new)) else putString(key(new), value) }
        }
        newIds.forEach { PinnedNoteWidget.update(context, it) }
    }
    private fun snapshotInstalled() {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, PinnedNoteWidget::class.java))
            .forEach { id -> if (!preferences.contains(key(id))) put(id, load(null)) }
    }
    private fun put(id: Int, state: PinnedNoteState) { preferences.edit { putString(key(id), encode(state.normalized())) } }
    private fun key(id: Int) = "widget.$id"
    private fun encode(state: PinnedNoteState) = JSONObject().put("title", state.title).put("text", state.text).put("style", state.style.name).toString()
    private fun decode(raw: String?): PinnedNoteState = runCatching {
        if (raw == null) return@runCatching PinnedNoteState()
        val json = JSONObject(raw)
        PinnedNoteState(json.optString("title", "NOTE"), json.optString("text", ""),
            runCatching { PinnedNoteStyle.valueOf(json.optString("style")) }.getOrDefault(PinnedNoteStyle.CARD)).normalized()
    }.getOrDefault(PinnedNoteState())
    private companion object { const val PREFS = "pinned-note"; const val DEFAULTS = "defaults"; val LOCK = Any() }
}

class PinnedNoteWidget : InstanceWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = PinnedNoteStore(context); ids.forEach { render(context, manager, it, store.load(it)) }
    }
    override fun onDeleted(context: Context, appWidgetIds: IntArray) { super.onDeleted(context, appWidgetIds); val s = PinnedNoteStore(context); appWidgetIds.forEach(s::delete) }
    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) { super.onRestored(context, oldWidgetIds, newWidgetIds); PinnedNoteStore(context).restore(oldWidgetIds, newWidgetIds) }
    companion object {
        fun update(context: Context, id: Int) = render(context, AppWidgetManager.getInstance(context), id, PinnedNoteStore(context).load(id))
        private fun render(context: Context, manager: AppWidgetManager, id: Int, state: PinnedNoteState) {
            val sizedViews = interactiveWidgetViews(manager.getAppWidgetOptions(id), 0) { width, height ->
                val views = RemoteViews(context.packageName, R.layout.widget_pinned_note)
                views.setThemedWidgetBitmap(context, R.id.pinned_note_image) { palette -> PinnedNoteRenderer.render(state, width, height, palette) }
                views.setOnClickPendingIntent(R.id.pinned_note_image, widgetPendingIntent(context, WidgetDestination.PINNED_NOTE, id))
                views.setContentDescription(R.id.pinned_note_root, "${state.title.ifBlank { "Pinned note" }}. ${state.text}")
                views
            }
            manager.updateAppWidget(id, sizedViews)
        }
    }
}

object PinnedNoteRenderer {
    fun render(rawState: PinnedNoteState, width: Int, height: Int, palette: WidgetPalette = WidgetPalette.current()): Bitmap {
        val state = rawState.normalized()
        val bitmap = card(width, height, palette)
        val canvas = Canvas(bitmap)
        val pad = width * .06f
        val titleColor = if (state.style == PinnedNoteStyle.TERMINAL) palette.accent else palette.foreground
        drawText(canvas, state.title.ifBlank { "NOTE" }.uppercase(Locale.getDefault()), pad, height * .17f,
            min(width / 280f, height / 104f) * 12f, titleColor)
        if (state.style == PinnedNoteStyle.CARD) {
            canvas.drawRect(pad, height * .22f, width - pad, height * .225f, Paint().apply { color = palette.inactive })
        }
        val prefix = if (state.style == PinnedNoteStyle.TERMINAL) "> " else ""
        drawWrapped(canvas, prefix + state.text.ifBlank { "Tap to write a note" }, pad, height * .34f,
            width - pad * 2, min(width / 280f, height / 160f) * 16f,
            if (state.text.isBlank()) palette.muted else palette.foreground, state.style == PinnedNoteStyle.MINIMAL)
        return bitmap
    }
}

private fun card(width: Int, height: Int, palette: WidgetPalette): Bitmap {
    val bitmap = createBitmap(width, height)
    Canvas(bitmap).drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), min(width, height) * .09f, min(width, height) * .09f,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.background })
    return bitmap
}

private fun drawText(canvas: Canvas, value: String, x: Float, baseline: Float, size: Float, color: Int, align: Paint.Align = Paint.Align.LEFT) {
    WidgetTypography.text(canvas, value, x, baseline, size, color, canvas.width * .88f, align, WidgetTypography.label)
}

private fun drawWrapped(canvas: Canvas, value: String, x: Float, startBaseline: Float, maxWidth: Float, size: Float, color: Int, spacious: Boolean) {
    val paint = android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        typeface = WidgetTypography.body
    }
    val top = startBaseline + paint.fontMetrics.ascent
    val availableHeight = canvas.height * .90f - top
    val spacing = if (spacious) 1.18f else 1f
    val lines = (availableHeight / (paint.fontSpacing * spacing)).toInt().coerceAtLeast(1)
    val layout = android.text.StaticLayout.Builder.obtain(value, 0, value.length, paint, maxWidth.toInt().coerceAtLeast(1))
        .setIncludePad(false)
        .setLineSpacing(0f, spacing)
        .setMaxLines(lines)
        .setEllipsize(android.text.TextUtils.TruncateAt.END)
        .build()
    canvas.save()
    canvas.clipRect(x, top, x + maxWidth, canvas.height * .90f)
    canvas.translate(x, top)
    layout.draw(canvas)
    canvas.restore()
}
