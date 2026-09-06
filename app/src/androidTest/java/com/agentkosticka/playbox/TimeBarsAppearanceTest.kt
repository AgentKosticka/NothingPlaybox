package com.agentkosticka.playbox

import android.graphics.Bitmap
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.ui.NothingDotFont
import com.agentkosticka.playbox.widget.TimeBarsRenderer
import java.io.File
import java.time.DayOfWeek
import java.time.Month
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimeBarsAppearanceTest {
    @Test fun calendarLabelsStayBeforeTheBars() {
        val labels = Month.entries.map { it.name } + DayOfWeek.entries.map { it.name }
        labels.forEach { label ->
            val fitted = TimeBarsRenderer.labelThatFits(label, 5.2f)
            assertTrue("$label renders as $fitted", TimeBarsRenderer.textWidth(fitted, 5.2f) <= 238f)
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = TimeBarsRenderer.render(ZonedDateTime.parse("2026-09-09T12:00:00+02:00"))
        // The label column ends at x=274; the first bar dot starts at x=281.6.
        for (y in 35 until 325) for (x in 275..280) {
            assertEquals("Label entered the gap at $x,$y", android.graphics.Color.rgb(17, 17, 17), bitmap.getPixel(x, y))
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
