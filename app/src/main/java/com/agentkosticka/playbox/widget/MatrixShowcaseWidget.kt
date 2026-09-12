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
import com.agentkosticka.playbox.MainActivity
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.data.EffectRepository
import com.agentkosticka.playbox.model.MATRIX_SIZE
import com.agentkosticka.playbox.model.PHONE_4A_PRO_MASK
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.ui.NothingDotFont
import java.util.Locale
import kotlin.math.min

/**
 * Home-screen window into Playbox's existing local effect library.
 *
 * This provider intentionally never acquires a Glyph Matrix lease. It only renders a local preview,
 * changes the repository's active effect on explicit user action, and opens Playbox for playback.
 */
class MatrixShowcaseWidget : InstanceWidgetProvider() {
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: android.os.Bundle) {
        onUpdate(context, manager, intArrayOf(id))
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val repository = EffectRepository(context)
        val store = MatrixShowcaseStore(context)
        ids.forEach { id -> render(context, manager, id, store.resolve(id, repository), repository.activeEffectId) }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = MatrixShowcaseStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        MatrixShowcaseStore(context).restore(oldWidgetIds, newWidgetIds)
        updateAll(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_PREVIOUS,
            ACTION_NEXT,
            ACTION_ACTIVE,
            ACTION_STYLE,
            -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val repository = EffectRepository(context)
                val store = MatrixShowcaseStore(context)
                when (intent.action) {
                    ACTION_PREVIOUS -> store.step(id, repository, -1)
                    ACTION_NEXT -> store.step(id, repository, 1)
                    ACTION_ACTIVE -> repository.setActiveEffect(store.resolve(id, repository).effect.id)
                    ACTION_STYLE -> store.cycleStyle(id, repository)
                }
                update(context, id)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_PREVIOUS = "com.agentkosticka.playbox.action.MATRIX_SHOWCASE_PREVIOUS"
        private const val ACTION_NEXT = "com.agentkosticka.playbox.action.MATRIX_SHOWCASE_NEXT"
        private const val ACTION_ACTIVE = "com.agentkosticka.playbox.action.MATRIX_SHOWCASE_ACTIVE"
        private const val ACTION_STYLE = "com.agentkosticka.playbox.action.MATRIX_SHOWCASE_STYLE"

        fun update(context: Context, id: Int) {
            val repository = EffectRepository(context)
            val selection = MatrixShowcaseStore(context).resolve(id, repository)
            render(context, AppWidgetManager.getInstance(context), id, selection, repository.activeEffectId)
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, MatrixShowcaseWidget::class.java))
            val repository = EffectRepository(context)
            val store = MatrixShowcaseStore(context)
            ids.forEach { id -> render(context, manager, id, store.resolve(id, repository), repository.activeEffectId) }
        }

        private fun render(
            context: Context,
            manager: AppWidgetManager,
            id: Int,
            selection: MatrixShowcaseSelection,
            activeId: String?,
        ) {
            val sizedViews = interactiveWidgetViews(manager.getAppWidgetOptions(id), 56) { width, height ->
                val views = widgetRemoteViews(context, R.layout.widget_matrix_showcase)
                views.setWidgetSurface(context, R.id.matrix_showcase_root, selection.style)
                val active = activeId == selection.effect.id
                views.setStyledWidgetBitmap(context, R.id.matrix_showcase_image, selection.style) { palette ->
                    MatrixShowcaseRenderer.render(selection.effect, active, width, height, palette.copy(background = android.graphics.Color.TRANSPARENT))
                }
                views.setTextViewText(R.id.matrix_showcase_active, context.getString(if (active) R.string.matrix_showcase_active else R.string.matrix_showcase_set_active))
                views.setOnClickPendingIntent(R.id.matrix_showcase_previous, action(context, id, ACTION_PREVIOUS, 1))
                views.setOnClickPendingIntent(R.id.matrix_showcase_next, action(context, id, ACTION_NEXT, 2))
                views.setOnClickPendingIntent(R.id.matrix_showcase_active, action(context, id, ACTION_ACTIVE, 3))
                views.setOnClickPendingIntent(R.id.matrix_showcase_style, action(context, id, ACTION_STYLE, 4))
                views.setOnClickPendingIntent(R.id.matrix_showcase_image, openPlaybox(context, id))
                views.setContentDescription(
                    R.id.matrix_showcase_root,
                    context.getString(
                        R.string.matrix_showcase_content_description,
                        selection.effect.name,
                        if (active) context.getString(R.string.matrix_showcase_active) else context.getString(R.string.matrix_showcase_not_active),
                    ),
                )
                views
            }
            manager.updateAppWidget(id, sizedViews)
        }

        private fun action(context: Context, id: Int, action: String, suffix: Int): PendingIntent {
            val intent = Intent(context, MatrixShowcaseWidget::class.java)
                .setAction(action)
                .setData(Uri.parse("playbox://matrix-showcase/$id/$suffix"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context,
                id * 10 + suffix,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun openPlaybox(context: Context, id: Int): PendingIntent = PendingIntent.getActivity(
            context,
            700_000 + id,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

data class MatrixShowcaseSelection(
    val effect: PlayboxEffect,
    val style: WidgetVisualStyle,
)

class MatrixShowcaseStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun resolve(id: Int, repository: EffectRepository): MatrixShowcaseSelection {
        val effects = repository.effects.value
        require(effects.isNotEmpty()) { "Playbox effect catalog is empty" }
        val requested = preferences.getString(effectKey(id), null)
        val effect = requested?.let(repository::find)
            ?: repository.activeEffectId?.let(repository::find)
            ?: effects.first()
        val style = runCatching {
            WidgetVisualStyle.valueOf(preferences.getString(styleKey(id), WidgetVisualStyle.DYNAMIC.name).orEmpty())
        }.getOrDefault(WidgetVisualStyle.DYNAMIC)
        if (requested != effect.id) preferences.edit { putString(effectKey(id), effect.id) }
        return MatrixShowcaseSelection(effect, style)
    }

    fun step(id: Int, repository: EffectRepository, direction: Int) {
        val effects = repository.effects.value
        if (effects.isEmpty()) return
        val current = resolve(id, repository).effect
        val index = effects.indexOfFirst { it.id == current.id }.takeIf { it >= 0 } ?: 0
        val next = ((index + direction) % effects.size + effects.size) % effects.size
        preferences.edit { putString(effectKey(id), effects[next].id) }
    }

    fun cycleStyle(id: Int, repository: EffectRepository) {
        val current = resolve(id, repository).style
        preferences.edit { putString(styleKey(id), current.next().name) }
    }

    fun delete(id: Int) {
        preferences.edit {
            remove(effectKey(id))
            remove(styleKey(id))
        }
    }

    fun restore(oldIds: IntArray, newIds: IntArray) {
        val saved = oldIds.zip(newIds).map { (old, new) ->
            Triple(new, preferences.getString(effectKey(old), null), preferences.getString(styleKey(old), null))
        }
        preferences.edit {
            oldIds.forEach { old ->
                remove(effectKey(old))
                remove(styleKey(old))
            }
            saved.forEach { (new, effect, style) ->
                if (effect != null) putString(effectKey(new), effect)
                if (style != null) putString(styleKey(new), style)
            }
        }
    }

    private fun effectKey(id: Int) = "widget.$id.effect"
    private fun styleKey(id: Int) = "widget.$id.style"

    private companion object {
        const val PREFS = "matrix-showcase"
    }
}

object MatrixShowcaseRenderer {
    fun render(
        effect: PlayboxEffect,
        active: Boolean,
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

        val frame = effect.frames.first()
        val scale = min(width / 280f, height / 104f)
        val pad = 16f * scale
        WidgetTypography.text(canvas, effect.name, pad, 20f * scale, 12f * scale,
            palette.foreground, width * .62f, face = WidgetTypography.label)
        WidgetTypography.text(canvas, if (active) "ACTIVE" else if (effect.isAnimated) "ANIMATED" else "STATIC",
            width - pad, 20f * scale, 9f * scale, palette.muted, width * .25f, Paint.Align.RIGHT)

        val gridHeight = height * .68f
        val gridSize = min(width * .45f, gridHeight)
        val cell = gridSize / MATRIX_SIZE
        val startX = (width - gridSize) / 2f
        val startY = height * .23f
        val baseRadius = cell * .29f
        repeat(MATRIX_SIZE * MATRIX_SIZE) { index ->
            if (!PHONE_4A_PRO_MASK[index]) return@repeat
            val row = index / MATRIX_SIZE
            val column = index % MATRIX_SIZE
            val value = frame.pixels[index].coerceIn(0, 255)
            val fraction = value / 255f
            val color = blend(palette.inactive, if (active) palette.accent else palette.foreground, fraction)
            canvas.drawCircle(
                startX + (column + .5f) * cell,
                startY + (row + .5f) * cell,
                baseRadius,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color },
            )
        }
        return bitmap
    }

    private fun blend(from: Int, to: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun channel(shift: Int): Int {
            val a = from ushr shift and 0xff
            val b = to ushr shift and 0xff
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}
