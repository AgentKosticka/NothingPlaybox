package com.agentkosticka.playbox

import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.agentkosticka.playbox.data.*
import com.agentkosticka.playbox.widget.*
import java.time.DayOfWeek
import java.time.ZonedDateTime
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

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

    @Test fun incompleteLegacyMigrationIsResumableAndLossless() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val v2Directory = context.filesDir.resolve("effects-v2")
        val legacyFile = context.filesDir.resolve("effects-v1.json")
        v2Directory.deleteRecursively()
        legacyFile.delete()

        val valid = JSONObject()
            .put("id", "legacy-resumable")
            .put("name", "Legacy resumable")
            .put("description", "Migration regression fixture")
            .put("loopMode", "LOOP")
            .put("createdAt", 1L)
            .put("updatedAt", 2L)
            .put("frames", JSONArray().put(
                JSONObject()
                    .put("durationMs", 100)
                    .put("pixels", Base64.encodeToString(ByteArray(169), Base64.NO_WRAP)),
            ))
        val invalid = JSONObject().put("id", "broken-entry")

        fun writeLegacy(vararg entries: JSONObject) {
            legacyFile.writeText(
                JSONObject()
                    .put("schema", PLAYBOX_SCHEMA_VERSION)
                    .put("effects", JSONArray().apply { entries.forEach(::put) })
                    .toString(),
            )
        }

        try {
            writeLegacy(valid, invalid)
            val first = EffectRepository(context)
            assertNotNull(first.find("legacy-resumable"))
            assertTrue("Incomplete migration must preserve the v1 source", legacyFile.exists())
            val migratedFiles = v2Directory.listFiles().orEmpty().map { it.name }.sorted()
            assertTrue(migratedFiles.isNotEmpty())

            val second = EffectRepository(context)
            assertNotNull(second.find("legacy-resumable"))
            assertTrue("Retry must still preserve an incomplete v1 source", legacyFile.exists())
            assertEquals(migratedFiles, v2Directory.listFiles().orEmpty().map { it.name }.sorted())

            // Once the legacy source is fully parseable, the already-migrated effect satisfies the
            // migration and the legacy file can finally be retired.
            writeLegacy(valid)
            val third = EffectRepository(context)
            assertNotNull(third.find("legacy-resumable"))
            assertFalse("Complete migration should retire v1 storage", legacyFile.exists())
        } finally {
            v2Directory.deleteRecursively()
            legacyFile.delete()
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

    @Test fun renderUtilityWidgetPackAcrossSizesAndModes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val now = ZonedDateTime.parse("2026-09-06T12:00:00+02:00[Europe/Prague]")
        val battery = BatteryInfo(percent = 67, charging = true, chargeRemainingMs = 4_500_000L)
        val storage = StorageInfo(totalBytes = 256_000_000_000L, freeBytes = 96_000_000_000L)
        val alarm = now.plusHours(7).plusMinutes(30)

        val ring = UtilityWidgetRenderer.batteryGlyph(battery, BatteryVisual.RING, 720, 320)
        val dots = UtilityWidgetRenderer.batteryGlyph(battery, BatteryVisual.DOTS, 720, 320)
        assertEquals(720, ring.width)
        assertEquals(320, ring.height)
        assertFalse(ring.sameAs(dots))

        val previews = listOf(
            UtilityWidgetRenderer.nextAlarm(context, now, alarm, 720, 320),
            UtilityWidgetRenderer.storage(storage, StorageDisplay.FREE, 360, 360),
            UtilityWidgetRenderer.month(now, DayOfWeek.MONDAY, 720, 360),
            UtilityWidgetRenderer.weekStrip(now, DayOfWeek.SUNDAY, 800, 300),
            UtilityWidgetRenderer.year(now, YearDisplay.REMAINING, 720, 360),
            UtilityWidgetRenderer.devicePanel(context, now, battery, storage, alarm, 720, 360),
            UtilityWidgetRenderer.milestone(now, MilestoneTarget.MONTH_END, 360, 360),
            UtilityWidgetRenderer.clockPreview(context, now, 720, 320),
            UtilityWidgetRenderer.shortcutsPreview(900, 300),
        )
        assertTrue(previews.all { it.width > 0 && it.height > 0 })
        assertTrue(previews.zipWithNext().all { (first, second) -> !first.sameAs(second) })
    }
}
