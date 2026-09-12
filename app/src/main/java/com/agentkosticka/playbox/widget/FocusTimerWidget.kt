package com.agentkosticka.playbox.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.agentkosticka.playbox.R
import com.agentkosticka.playbox.ui.NothingDotFont
import org.json.JSONObject
import java.util.Locale
import kotlin.math.min

/** A focus timer whose displayed state is derived from persisted endpoints, never polling ticks. */
enum class FocusPhase {
    IDLE,
    FOCUS,
    BREAK,
    PAUSED_FOCUS,
    PAUSED_BREAK;

    val running: Boolean get() = this == FOCUS || this == BREAK
    val paused: Boolean get() = this == PAUSED_FOCUS || this == PAUSED_BREAK
}

data class FocusTimerState(
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val phase: FocusPhase = FocusPhase.IDLE,
    val endWallMillis: Long = 0L,
    val startElapsedMillis: Long = 0L,
    val endElapsedMillis: Long = 0L,
    val pausedRemainingMillis: Long = 25 * 60_000L,
    val completedSessions: Int = 0,
    val style: WidgetVisualStyle = WidgetVisualStyle.DYNAMIC,
) {
    fun normalized(): FocusTimerState = copy(
        focusMinutes = focusMinutes.coerceIn(10, 120),
        breakMinutes = breakMinutes.coerceIn(1, 60),
        endWallMillis = endWallMillis.coerceAtLeast(0L),
        startElapsedMillis = startElapsedMillis.coerceAtLeast(0L),
        endElapsedMillis = endElapsedMillis.coerceAtLeast(0L),
        pausedRemainingMillis = pausedRemainingMillis.coerceIn(0L, 3 * 60 * 60_000L),
        completedSessions = completedSessions.coerceIn(0, 9_999),
    )

    fun phaseDurationMillis(): Long = when (phase) {
        FocusPhase.BREAK, FocusPhase.PAUSED_BREAK -> breakMinutes * 60_000L
        else -> focusMinutes * 60_000L
    }

    fun remainingMillis(nowWall: Long = System.currentTimeMillis(), nowElapsed: Long = SystemClock.elapsedRealtime()): Long {
        if (phase.paused) return pausedRemainingMillis.coerceAtLeast(0L)
        if (!phase.running) return focusMinutes * 60_000L
        val sameBoot = startElapsedMillis > 0L && nowElapsed >= startElapsedMillis && endElapsedMillis >= startElapsedMillis
        val remaining = if (sameBoot) endElapsedMillis - nowElapsed else endWallMillis - nowWall
        return remaining.coerceAtLeast(0L)
    }
}

object FocusTimerEngine {
    fun startFocus(state: FocusTimerState, nowWall: Long, nowElapsed: Long): FocusTimerState =
        startPhase(state, FocusPhase.FOCUS, state.focusMinutes * 60_000L, nowWall, nowElapsed)

    fun pause(state: FocusTimerState, nowWall: Long, nowElapsed: Long): FocusTimerState {
        if (!state.phase.running) return state
        val pausedPhase = if (state.phase == FocusPhase.FOCUS) FocusPhase.PAUSED_FOCUS else FocusPhase.PAUSED_BREAK
        return state.copy(
            phase = pausedPhase,
            pausedRemainingMillis = state.remainingMillis(nowWall, nowElapsed),
            endWallMillis = 0L,
            startElapsedMillis = 0L,
            endElapsedMillis = 0L,
        )
    }

    fun resume(state: FocusTimerState, nowWall: Long, nowElapsed: Long): FocusTimerState {
        if (!state.phase.paused) return state
        val runningPhase = if (state.phase == FocusPhase.PAUSED_FOCUS) FocusPhase.FOCUS else FocusPhase.BREAK
        return startPhase(state, runningPhase, state.pausedRemainingMillis.coerceAtLeast(1_000L), nowWall, nowElapsed)
    }

    fun reset(state: FocusTimerState): FocusTimerState = state.copy(
        phase = FocusPhase.IDLE,
        endWallMillis = 0L,
        startElapsedMillis = 0L,
        endElapsedMillis = 0L,
        pausedRemainingMillis = state.focusMinutes * 60_000L,
    )

    fun skip(state: FocusTimerState, nowWall: Long, nowElapsed: Long): FocusTimerState = when (state.phase) {
        FocusPhase.FOCUS, FocusPhase.PAUSED_FOCUS -> startPhase(
            state,
            FocusPhase.BREAK,
            state.breakMinutes * 60_000L,
            nowWall,
            nowElapsed,
        )
        FocusPhase.BREAK, FocusPhase.PAUSED_BREAK -> reset(state)
        FocusPhase.IDLE -> state
    }

    fun reconcile(state: FocusTimerState, nowWall: Long, nowElapsed: Long): FocusTimerState {
        if (!state.phase.running || state.remainingMillis(nowWall, nowElapsed) > 0L) return state
        return if (state.phase == FocusPhase.FOCUS) {
            startPhase(
                state.copy(completedSessions = state.completedSessions + 1),
                FocusPhase.BREAK,
                state.breakMinutes * 60_000L,
                nowWall,
                nowElapsed,
            )
        } else {
            reset(state)
        }
    }

    private fun startPhase(
        state: FocusTimerState,
        phase: FocusPhase,
        durationMillis: Long,
        nowWall: Long,
        nowElapsed: Long,
    ): FocusTimerState = state.copy(
        phase = phase,
        endWallMillis = nowWall + durationMillis,
        startElapsedMillis = nowElapsed,
        endElapsedMillis = nowElapsed + durationMillis,
        pausedRemainingMillis = durationMillis,
    ).normalized()
}

class FocusTimerStore(private val context: Context) {
    private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(id: Int?): FocusTimerState = synchronized(LOCK) {
        val state = if (id == null) decode(preferences.getString(DEFAULTS, null))
        else preferences.getString(key(id), null)?.let(::decode) ?: load(null).let {
            // Defaults are configuration only; never clone a running session into a new widget.
            val initial = FocusTimerEngine.reset(it).copy(completedSessions = 0)
            put(id, initial)
            initial
        }
        val reconciled = FocusTimerEngine.reconcile(state, System.currentTimeMillis(), SystemClock.elapsedRealtime())
        if (id != null && reconciled != state) put(id, reconciled)
        reconciled
    }

    fun startPause(id: Int) = mutate(id) { state, wall, elapsed ->
        when {
            state.phase == FocusPhase.IDLE -> FocusTimerEngine.startFocus(state, wall, elapsed)
            state.phase.running -> FocusTimerEngine.pause(state, wall, elapsed)
            else -> FocusTimerEngine.resume(state, wall, elapsed)
        }
    }

    fun reset(id: Int) = mutate(id) { state, _, _ -> FocusTimerEngine.reset(state) }
    fun skip(id: Int) = mutate(id) { state, wall, elapsed -> FocusTimerEngine.skip(state, wall, elapsed) }
    fun cycleStyle(id: Int) = mutate(id) { state, _, _ -> state.copy(style = state.style.next()) }

    fun finishSignal(id: Int) = mutate(id) { state, wall, elapsed -> FocusTimerEngine.reconcile(state, wall, elapsed) }

    fun delete(id: Int) = synchronized(LOCK) {
        FocusTimerScheduler.cancel(context, id)
        preferences.edit { remove(key(id)) }
    }

    fun restore(oldIds: IntArray, newIds: IntArray) = synchronized(LOCK) {
        val saved = oldIds.zip(newIds).map { (old, new) -> new to preferences.getString(key(old), null) }
        oldIds.forEach { FocusTimerScheduler.cancel(context, it) }
        preferences.edit {
            oldIds.forEach { remove(key(it)) }
            saved.forEach { (new, raw) ->
                // Active focus sessions are deliberately not restored across widget/device restore.
                val restored = FocusTimerEngine.reset(decode(raw)).copy(completedSessions = decode(raw).completedSessions)
                putString(key(new), encode(restored))
            }
        }
        newIds.forEach { id ->
            FocusTimerWidget.update(context, id)
            FocusTimerScheduler.schedule(context, id, load(id))
        }
    }

    private inline fun mutate(
        id: Int,
        transform: (FocusTimerState, Long, Long) -> FocusTimerState,
    ) = synchronized(LOCK) {
        val wall = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val current = FocusTimerEngine.reconcile(load(id), wall, elapsed)
        val next = transform(current, wall, elapsed).normalized()
        put(id, next)
        FocusTimerScheduler.schedule(context, id, next)
        FocusTimerWidget.update(context, id)
    }

    private fun put(id: Int, state: FocusTimerState) {
        preferences.edit { putString(key(id), encode(state.normalized())) }
    }

    private fun key(id: Int) = "widget.$id"

    private fun encode(state: FocusTimerState): String = JSONObject()
        .put("focusMinutes", state.focusMinutes)
        .put("breakMinutes", state.breakMinutes)
        .put("phase", state.phase.name)
        .put("endWallMillis", state.endWallMillis)
        .put("startElapsedMillis", state.startElapsedMillis)
        .put("endElapsedMillis", state.endElapsedMillis)
        .put("pausedRemainingMillis", state.pausedRemainingMillis)
        .put("completedSessions", state.completedSessions)
        .put("style", state.style.name)
        .toString()

    private fun decode(raw: String?): FocusTimerState = runCatching {
        if (raw == null) return@runCatching FocusTimerState()
        val json = JSONObject(raw)
        FocusTimerState(
            focusMinutes = json.optInt("focusMinutes", 25),
            breakMinutes = json.optInt("breakMinutes", 5),
            phase = runCatching { FocusPhase.valueOf(json.optString("phase", FocusPhase.IDLE.name)) }
                .getOrDefault(FocusPhase.IDLE),
            endWallMillis = json.optLong("endWallMillis", 0L),
            startElapsedMillis = json.optLong("startElapsedMillis", 0L),
            endElapsedMillis = json.optLong("endElapsedMillis", 0L),
            pausedRemainingMillis = json.optLong("pausedRemainingMillis", 25 * 60_000L),
            completedSessions = json.optInt("completedSessions", 0),
            style = runCatching { WidgetVisualStyle.valueOf(json.optString("style", WidgetVisualStyle.DYNAMIC.name)) }
                .getOrDefault(WidgetVisualStyle.DYNAMIC),
        ).normalized()
    }.getOrDefault(FocusTimerState())

    private companion object {
        const val PREFS = "focus-timer"
        const val DEFAULTS = "defaults"
        val LOCK = Any()
    }
}

object FocusTimerScheduler {
    fun hasPreciseAccess(context: Context): Boolean =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(context: Context, id: Int, state: FocusTimerState) {
        cancel(context, id)
        if (!state.phase.running) return
        val remaining = state.remainingMillis()
        if (remaining <= 0L) return
        val manager = context.getSystemService(AlarmManager::class.java)
        val trigger = SystemClock.elapsedRealtime() + remaining
        val pending = finishIntent(context, id)
        if (manager.canScheduleExactAlarms()) {
            runCatching { manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending) }
                .onFailure { manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending) }
        } else {
            manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pending)
        }
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(finishIntent(context, id))
    }

    private fun finishIntent(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, FocusTimerWidget::class.java)
            .setAction(FocusTimerWidget.ACTION_FINISH)
            .setData(Uri.parse("playbox://focus/$id/finish"))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        return PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

class FocusTimerWidget : InstanceWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val store = FocusTimerStore(context)
        ids.forEach { id ->
            val state = store.load(id)
            FocusTimerScheduler.schedule(context, id, state)
            render(context, manager, id, state)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val store = FocusTimerStore(context)
        appWidgetIds.forEach(store::delete)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        FocusTimerStore(context).restore(oldWidgetIds, newWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_START_PAUSE, ACTION_RESET, ACTION_SKIP, ACTION_STYLE, ACTION_FINISH -> {
                val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
                val store = FocusTimerStore(context)
                when (intent.action) {
                    ACTION_START_PAUSE -> store.startPause(id)
                    ACTION_RESET -> store.reset(id)
                    ACTION_SKIP -> store.skip(id)
                    ACTION_STYLE -> store.cycleStyle(id)
                    ACTION_FINISH -> store.finishSignal(id)
                }
                return
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                updateAll(context)
                return
            }
        }
        super.onReceive(context, intent)
    }

    companion object {
        internal const val ACTION_FINISH = "com.agentkosticka.playbox.action.FOCUS_FINISH"
        private const val ACTION_START_PAUSE = "com.agentkosticka.playbox.action.FOCUS_START_PAUSE"
        private const val ACTION_RESET = "com.agentkosticka.playbox.action.FOCUS_RESET"
        private const val ACTION_SKIP = "com.agentkosticka.playbox.action.FOCUS_SKIP"
        private const val ACTION_STYLE = "com.agentkosticka.playbox.action.FOCUS_STYLE"

        fun update(context: Context, id: Int) {
            render(context, AppWidgetManager.getInstance(context), id, FocusTimerStore(context).load(id))
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val store = FocusTimerStore(context)
            manager.getAppWidgetIds(ComponentName(context, FocusTimerWidget::class.java)).forEach { id ->
                val state = store.load(id)
                FocusTimerScheduler.schedule(context, id, state)
                render(context, manager, id, state)
            }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int, state: FocusTimerState) {
            val views = RemoteViews(context.packageName, R.layout.widget_focus_timer)
            val remaining = state.remainingMillis()
            val phaseLabel = context.getString(
                when (state.phase) {
                    FocusPhase.FOCUS -> R.string.focus_phase_focus
                    FocusPhase.BREAK -> R.string.focus_phase_break
                    FocusPhase.PAUSED_FOCUS, FocusPhase.PAUSED_BREAK -> R.string.focus_phase_paused
                    FocusPhase.IDLE -> R.string.focus_phase_ready
                },
            )
            val sessions = context.resources.getQuantityString(
                R.plurals.focus_sessions,
                state.completedSessions,
                state.completedSessions,
            )
            views.setStyledWidgetBitmap(context, R.id.focus_background, state.style) { palette ->
                FocusTimerRenderer.render(state, remaining, phaseLabel, sessions, 720, 300, palette)
            }

            val running = state.phase.running
            views.setViewVisibility(R.id.focus_chronometer, if (running) View.VISIBLE else View.GONE)
            views.setViewVisibility(R.id.focus_static_time, if (running) View.GONE else View.VISIBLE)
            if (running) {
                val base = SystemClock.elapsedRealtime() + remaining
                views.setChronometer(R.id.focus_chronometer, base, null, true)
                views.setChronometerCountDown(R.id.focus_chronometer, true)
            } else {
                views.setTextViewText(R.id.focus_static_time, formatDuration(remaining))
            }

            views.setTextViewText(
                R.id.focus_start_pause,
                context.getString(
                    when {
                        state.phase.running -> R.string.focus_pause
                        state.phase.paused -> R.string.focus_resume
                        else -> R.string.focus_start
                    },
                ),
            )
            views.setViewVisibility(R.id.focus_skip, if (state.phase == FocusPhase.IDLE) View.INVISIBLE else View.VISIBLE)
            views.setViewVisibility(
                R.id.focus_precise,
                if (FocusTimerScheduler.hasPreciseAccess(context)) View.GONE else View.VISIBLE,
            )

            views.setOnClickPendingIntent(R.id.focus_start_pause, action(context, id, ACTION_START_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.focus_reset, action(context, id, ACTION_RESET, 2))
            views.setOnClickPendingIntent(R.id.focus_skip, action(context, id, ACTION_SKIP, 3))
            views.setOnClickPendingIntent(R.id.focus_style, action(context, id, ACTION_STYLE, 4))
            views.setOnClickPendingIntent(R.id.focus_precise, preciseAccessIntent(context, id))
            views.setContentDescription(
                R.id.focus_root,
                context.getString(R.string.focus_content_description, phaseLabel, formatDuration(remaining), state.completedSessions),
            )
            manager.updateAppWidget(id, views)
        }

        private fun action(context: Context, id: Int, action: String, suffix: Int): PendingIntent {
            val intent = Intent(context, FocusTimerWidget::class.java)
                .setAction(action)
                .setData(Uri.parse("playbox://focus/$id/$suffix"))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            return PendingIntent.getBroadcast(
                context,
                id * 10 + suffix,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun preciseAccessIntent(context: Context, id: Int): PendingIntent {
            val intent = Intent(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return PendingIntent.getActivity(
                context,
                id,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        internal fun formatDuration(millis: Long): String {
            val totalSeconds = (millis.coerceAtLeast(0L) + 999L) / 1_000L
            val minutes = totalSeconds / 60L
            val seconds = totalSeconds % 60L
            return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
        }
    }
}

object FocusTimerRenderer {
    fun render(
        state: FocusTimerState,
        remainingMillis: Long,
        phaseLabel: String,
        sessionsLabel: String,
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
        canvas.drawText(phaseLabel.uppercase(Locale.getDefault()), pad, height * .18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.foreground
            textSize = min(width, height) * .067f
            typeface = dot
        })
        canvas.drawText(sessionsLabel.uppercase(Locale.getDefault()), width - pad, height * .18f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.muted
            textSize = min(width, height) * .045f
            typeface = dot
            textAlign = Paint.Align.RIGHT
        })

        val duration = state.phaseDurationMillis().coerceAtLeast(1L)
        val progress = (1f - remainingMillis.coerceIn(0L, duration).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        val barLeft = pad
        val barRight = width - pad
        val barTop = height * .72f
        val barBottom = height * .755f
        canvas.drawRoundRect(barLeft, barTop, barRight, barBottom, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.inactive })
        canvas.drawRoundRect(barLeft, barTop, barLeft + (barRight - barLeft) * progress, barBottom, 12f, 12f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.accent })

        val dots = 12
        val activeDots = (progress * dots).toInt().coerceIn(0, dots)
        repeat(dots) { index ->
            val x = barLeft + (barRight - barLeft) * (index + .5f) / dots
            canvas.drawCircle(x, height * .86f, min(width, height) * .012f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (index < activeDots) palette.accent else palette.inactive
            })
        }
        return bitmap
    }
}
