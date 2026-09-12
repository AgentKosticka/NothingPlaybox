package com.agentkosticka.playbox

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.widget.*
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Render real RemoteViews, including native controls, at launcher sizes for visual review. */
@RunWith(AndroidJUnit4::class)
class WidgetDesignTest {
    @Test fun appearanceEditsPreserveLiveProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val focus = FocusTimerStore(context)
        val goals = GoalTrackerStore(context)
        val id = 9876543
        try {
            focus.startPause(id)
            val running = focus.load(id)
            focus.setAppearance(id, WidgetVisualStyle.HIGH_CONTRAST)
            val restyled = focus.load(id)
            org.junit.Assert.assertEquals(running.endElapsedMillis, restyled.endElapsedMillis)
            org.junit.Assert.assertEquals(running.phase, restyled.phase)
            goals.change(id, 1)
            val logged = goals.load(id)
            goals.configure(id, logged.preset, GoalVisual.DOTS, WidgetVisualStyle.CLASSIC)
            org.junit.Assert.assertEquals(logged.value, goals.load(id).value)
            org.junit.Assert.assertEquals(logged.history, goals.load(id).history)
        } finally {
            focus.delete(id)
            goals.delete(id)
        }
    }

    @Test fun renderLauncherReviewSheets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val base = instrumentation.targetContext
        for (night in listOf(false, true)) for (width in listOf(220, 320)) {
            val config = Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            val context = base.createConfigurationContext(config)
            val density = context.resources.displayMetrics.density
            val w = (width * density).toInt()
            val height = if (width == 220) 140 else 180
            val h = (height * density).toInt()
            val imageH = ((height - 56) * density).toInt()
            val palette = WidgetPalette.resolve(context)
            val layouts = listOf(R.layout.widget_focus_timer, R.layout.widget_goal_tracker,
                R.layout.widget_tally_counter, R.layout.widget_pinned_note, R.layout.widget_matrix_showcase,
                R.layout.widget_quick_tasks, R.layout.widget_dual_clock, R.layout.widget_habit_tracker,
                R.layout.widget_next_event)
            val sheet = Bitmap.createBitmap(w * 3 + 64, h * 3 + 64, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(sheet).apply { drawColor(Color.rgb(100, 104, 102)) }
            instrumentation.runOnMainSync {
                layouts.forEachIndexed { index, layout ->
                    val views = RemoteViews(context.packageName, layout)
                    when (layout) {
                        R.layout.widget_dual_clock -> {
                            views.setTextViewText(R.id.dual_clock_local_time, "10:08")
                            views.setTextViewText(R.id.dual_clock_remote_time, "08:08")
                            views.setTextViewText(R.id.dual_clock_local_date, "Sat 12 Sep · AM")
                            views.setTextViewText(R.id.dual_clock_remote_date, "Sat 12 Sep · AM")
                        }
                        R.layout.widget_focus_timer -> views.setImageViewBitmap(R.id.focus_background,
                            FocusTimerRenderer.render(FocusTimerState(), 1500000, "FOCUS", "3 sessions", w, imageH, palette, false))
                        R.layout.widget_goal_tracker -> views.setImageViewBitmap(R.id.goal_background,
                            GoalTrackerRenderer.render(GoalTrackerState(value = 5), "Water", "glasses", w, imageH, palette))
                        R.layout.widget_tally_counter -> views.setImageViewBitmap(R.id.tally_image,
                            TallyRenderer.render(TallyState(label = "Laps", value = 42), w, imageH, palette))
                        R.layout.widget_pinned_note -> views.setImageViewBitmap(R.id.pinned_note_image,
                            PinnedNoteRenderer.render(PinnedNoteState(title = "A little reminder", text = "Make room for what matters.\n\nWalk. Read. Call home."), w, h, palette))
                        R.layout.widget_matrix_showcase -> views.setImageViewBitmap(R.id.matrix_showcase_image,
                            MatrixShowcaseRenderer.render(PlayboxEffect(name = "Orbit", frames = listOf(EffectFrame(IntArray(169) { if (it % 3 == 0) 255 else 25 }))), true, w, imageH, palette))
                        R.layout.widget_habit_tracker -> views.setImageViewBitmap(R.id.habit_tracker_grid,
                            HabitTrackerRenderer.monthGrid(HabitTrackerState(), LocalDate.of(2026, 9, 12), 720, 220, palette))
                    }
                    val view = views.apply(context, FrameLayout(context))
                    // Autosizing TextClocks request a second layout, as they do in a launcher.
                    repeat(3) {
                        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
                        view.layout(0, 0, w, h)
                    }
                    if (layout == R.layout.widget_dual_clock) {
                        listOf(R.id.dual_clock_local_time, R.id.dual_clock_remote_time).forEach { id ->
                            val clock = view.findViewById<android.widget.TextClock>(id)
                            org.junit.Assert.assertEquals(1, clock.layout.lineCount)
                            org.junit.Assert.assertEquals(0, clock.layout.getEllipsisCount(0))
                        }
                    }
                    assertTrue(view.measuredWidth == w && view.measuredHeight == h)
                    canvas.save()
                    canvas.translate((16 + index % 3 * (w + 16)).toFloat(), (16 + index / 3 * (h + 16)).toFloat())
                    // Software Canvas does not apply View.clipToOutline; mirror the native shell.
                    val outline = android.graphics.Path().apply {
                        addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), 28f * density, 28f * density, android.graphics.Path.Direction.CW)
                    }
                    canvas.clipPath(outline)
                    view.draw(canvas)
                    canvas.restore()
                }
            }
            File(base.getExternalFilesDir(null), "widget-design-${if (night) "dark" else "light"}-$width.png")
                .outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
            sheet.recycle()
        }
    }
}
