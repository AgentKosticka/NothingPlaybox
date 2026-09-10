package com.agentkosticka.playbox

import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.ui.NothingDotFont
import com.agentkosticka.playbox.widget.TimeBarsRenderer
import com.agentkosticka.playbox.widget.UtilityWidgetRenderer
import com.agentkosticka.playbox.widget.BatteryInfo
import com.agentkosticka.playbox.widget.WidgetPalette
import java.io.File
import java.time.DayOfWeek
import java.time.Month
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeBarsAppearanceTest {
    @Test fun responsiveWidgetsKeepTheirShapeAndContent() {
        val now = ZonedDateTime.parse("2026-09-10T12:00:00+02:00")
        listOf(350 to 140, 140 to 350, 55 to 110).forEach { (w, h) ->
            val (bw, bh) = UtilityWidgetRenderer.bitmapSize(w, h)
            assertEquals(w.toFloat() / h, bw.toFloat() / bh, .005f)
        }
        val palette = WidgetPalette.fallbackDark
        val wide = TimeBarsRenderer.render(now, width = 900, height = 360, palette = palette)
        // A dot in the wide layout has the same diameter along both axes.
        val cx = 285
        val cy = 51
        val horizontal = (cx - 5..cx + 5).count { wide.getPixel(it, cy) != palette.background }
        val vertical = (cy - 5..cy + 5).count { wide.getPixel(cx, it) != palette.background }
        assertTrue(horizontal > 0)
        assertEquals(horizontal, vertical)
        val battery = UtilityWidgetRenderer.batteryColumn(BatteryInfo(68, true, null), 180, 360)
        val week = UtilityWidgetRenderer.weekColumn(now, DayOfWeek.MONDAY, 180, 360)
        val sheet = Bitmap.createBitmap(900, 800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sheet)
        canvas.drawColor(Color.rgb(30, 30, 30))
        canvas.drawBitmap(wide, 0f, 0f, null)
        canvas.drawBitmap(battery, 30f, 400f, null)
        canvas.drawBitmap(week, 240f, 400f, null)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.getExternalFilesDir(null), "responsive-widgets-review.png").outputStream().use {
            sheet.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        listOf(wide, battery, week, sheet).forEach { it.recycle() }
    }

    @Test fun calendarLabelsStayBeforeTheBars() {
        val labels = Month.entries.map { it.name } + DayOfWeek.entries.map { it.name }
        labels.forEach { label ->
            val fitted = TimeBarsRenderer.labelThatFits(label, 5.2f)
            assertTrue("$label renders as $fitted", TimeBarsRenderer.textWidth(fitted, 5.2f) <= 238f)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val palette = WidgetPalette.fallbackDark
        val bitmap = TimeBarsRenderer.render(
            ZonedDateTime.parse("2026-09-09T12:00:00+02:00"),
            palette = palette,
        )
        // The label column ends at x=274; the first bar dot starts at x=281.6.
        for (y in 35 until 325) for (x in 275..280) {
            assertEquals("Label entered the gap at $x,$y", palette.background, bitmap.getPixel(x, y))
        }
        File(context.getExternalFilesDir(null), "time-bars-review.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }

    @Test fun installedNothingFontIsUsed() {
        val installed = listOf("NDot57Caps.otf", "Ndot-57.otf", "Ndot-57-Aligned.otf")
            .any { File("/system/fonts", it).canRead() }
        if (installed || Typeface.create("NDot57", Typeface.NORMAL) != Typeface.DEFAULT) {
            assertTrue(NothingDotFont.available)
            assertNotEquals(Typeface.MONOSPACE, NothingDotFont.typeface)
            assertNotEquals(Typeface.DEFAULT, NothingDotFont.typeface)
        } else {
            assertFalse(NothingDotFont.available)
            assertEquals(Typeface.MONOSPACE, NothingDotFont.typeface)
        }
    }
}
