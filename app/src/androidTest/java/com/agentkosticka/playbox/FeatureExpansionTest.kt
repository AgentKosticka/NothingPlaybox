package com.agentkosticka.playbox

import android.graphics.Bitmap
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.agentkosticka.playbox.data.*
import com.agentkosticka.playbox.widget.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZonedDateTime

@RunWith(AndroidJUnit4::class)
class FeatureExpansionTest {
    @Test fun profilesSurviveStorageAndArchiveRoundTrips() = runBlocking {
        // Use the test APK's own storage, never the user's library.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = EffectRepository(context)
        EffectCatalog.builtIns.filter { it.procedural != null }.forEach { template ->
            val saved = repository.save(template.editableCopy("Test profile"))
            val file = context.cacheDir.resolve("profile.playbox")
            try {
                assertEquals(saved.procedural, EffectRepository(context).find(saved.id)?.procedural)
                repository.exportEffect(saved, context.contentResolver, Uri.fromFile(file))
                val imported = repository.importEffect(context.contentResolver, Uri.fromFile(file))
                assertEquals(saved.procedural, imported.procedural)
                assertNotEquals(saved.id, imported.id)
                repository.delete(imported.id)
            } finally { repository.delete(saved.id); file.delete() }
        }
    }

    @Test fun renderWidgetAndBloomReviewImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = context.getExternalFilesDir(null)!!.resolve("feature-previews").apply { mkdirs() }
        val now = ZonedDateTime.parse("2026-09-06T12:00:00+02:00[Europe/Prague]")
        val dial = DashboardRenderer.dayDial(now)
        val battery = DashboardRenderer.battery(75, true)
        assertEquals(360, dial.width)
        assertFalse(battery.sameAs(DashboardRenderer.battery(25, false)))
        mapOf("day_dial_preview.png" to dial, "battery_dots_preview.png" to battery).forEach { (name, bitmap) ->
            directory.resolve(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val runtime = ProceduralEffectRuntime(OrganicEffects.all.single())
        val sheet = Bitmap.createBitmap(13 * 16 * 4, 13 * 16, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(sheet)
        val paint = android.graphics.Paint()
        listOf(0L, 3000L, 6000L, 9000L).forEachIndexed { sample, time ->
            val pixels = runtime.frameAt(time).pixels
            pixels.forEachIndexed { index, value ->
                paint.color = android.graphics.Color.rgb(value, value, value)
                canvas.drawRect((sample * 208 + index % 13 * 16).toFloat(), (index / 13 * 16).toFloat(), (sample * 208 + index % 13 * 16 + 14).toFloat(), (index / 13 * 16 + 14).toFloat(), paint)
            }
        }
        directory.resolve("bloom-evolution.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
