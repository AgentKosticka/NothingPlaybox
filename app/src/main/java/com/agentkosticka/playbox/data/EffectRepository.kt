package com.agentkosticka.playbox.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import android.util.Base64
import androidx.core.content.edit
import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.LoopMode
import com.agentkosticka.playbox.model.MAX_EFFECT_FRAMES
import com.agentkosticka.playbox.model.PIXEL_COUNT
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.ProceduralSpec
import com.agentkosticka.playbox.model.normalized
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class EffectRepository(context: Context) {
    private val appContext = context.applicationContext
    private val storage = AtomicFile(appContext.filesDir.resolve("effects-v1.json"))
    private val preferences = appContext.getSharedPreferences("playbox", Context.MODE_PRIVATE)
    private var userEffects: MutableList<PlayboxEffect> = loadUsers().toMutableList()
    private val _effects = MutableStateFlow(mergedEffects())

    val effects: StateFlow<List<PlayboxEffect>> = _effects.asStateFlow()
    val activeEffectId: String?
        get() = preferences.getString(KEY_ACTIVE_EFFECT, null)

    fun find(id: String): PlayboxEffect? = _effects.value.firstOrNull { it.id == id }

    @Synchronized
    fun save(effect: PlayboxEffect): PlayboxEffect {
        var normalized = effect.copy(
            frames = effect.frames.map(EffectFrame::normalized),
            procedural = effect.procedural?.normalized(),
            builtIn = false,
            updatedAt = System.currentTimeMillis(),
        )
        if (normalized.procedural != null) {
            normalized = normalized.copy(frames = listOf(ProceduralEffectRuntime(normalized).frameAt(0)))
        }
        val index = userEffects.indexOfFirst { it.id == normalized.id }
        if (index >= 0) userEffects[index] = normalized else userEffects.add(0, normalized)
        persistUsers()
        _effects.value = mergedEffects()
        return normalized
    }

    @Synchronized
    fun delete(id: String) {
        userEffects.removeAll { it.id == id }
        if (activeEffectId == id) setActiveEffect(EffectCatalog.builtIns.first().id)
        persistUsers()
        _effects.value = mergedEffects()
    }

    fun setActiveEffect(id: String) {
        preferences.edit { putString(KEY_ACTIVE_EFFECT, id) }
    }

    suspend fun exportEffect(effect: PlayboxEffect, resolver: ContentResolver, uri: Uri) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "w")?.use { output ->
            ZipOutputStream(output).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(
                    effectToJson(effect)
                        .put("schema", PLAYBOX_SCHEMA_VERSION)
                        .toString(2)
                        .toByteArray(Charsets.UTF_8),
                )
                zip.closeEntry()
            }
        } ?: error("Unable to open export destination")
    }

    suspend fun importEffect(resolver: ContentResolver, uri: Uri): PlayboxEffect = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use(::readPlayboxManifest)
            ?: error("Unable to read effect")
        val manifest = JSONObject(String(bytes, Charsets.UTF_8))
        require(manifest.optInt("schema", 0) == PLAYBOX_SCHEMA_VERSION) { "Unsupported Playbox effect version" }
        val decoded = effectFromJson(manifest)
        save(decoded.editableCopy(decoded.name))
    }

    private fun mergedEffects() = userEffects.sortedByDescending { it.updatedAt } + EffectCatalog.builtIns

    private fun loadUsers(): List<PlayboxEffect> = runCatching {
        if (!storage.baseFile.exists()) return@runCatching emptyList()
        val root = storage.openRead().bufferedReader().use { JSONObject(it.readText()) }
        require(root.optInt("schema", 0) == PLAYBOX_SCHEMA_VERSION)
        val array = root.getJSONArray("effects")
        buildList {
            for (index in 0 until array.length()) {
                try {
                    add(effectFromJson(array.getJSONObject(index)))
                } catch (_: Exception) {
                    // Preserve the rest of the library when a single stored effect is malformed.
                }
            }
        }
    }.getOrDefault(emptyList())

    private fun persistUsers() {
        val root = JSONObject().put("schema", PLAYBOX_SCHEMA_VERSION).put("effects", JSONArray().apply {
            userEffects.forEach { put(effectToJson(it)) }
        })
        val output = storage.startWrite()
        try {
            output.write(root.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            storage.finishWrite(output)
        } catch (error: Throwable) {
            storage.failWrite(output)
            throw error
        }
    }

    private fun effectToJson(effect: PlayboxEffect) = JSONObject()
        .put("id", effect.id)
        .put("name", effect.name)
        .put("description", effect.description)
        .put("loopMode", effect.loopMode.name)
        .put("createdAt", effect.createdAt)
        .put("updatedAt", effect.updatedAt)
        .apply { effect.procedural?.let { put("procedural", proceduralToJson(it)) } }
        .put("frames", JSONArray().apply {
            effect.frames.forEach { frame ->
                val bytes = ByteArray(PIXEL_COUNT) { frame.pixels[it].coerceIn(0, 255).toByte() }
                put(JSONObject()
                    .put("durationMs", frame.durationMs)
                    .put("pixels", Base64.encodeToString(bytes, Base64.NO_WRAP)))
            }
        })

    private fun proceduralToJson(spec: ProceduralSpec): JSONObject = when (spec) {
        is ProceduralSpec.ConwayLife -> {
            val bytes = ByteArray(PIXEL_COUNT) { if (spec.initialState[it] > 0) 1 else 0 }
            JSONObject()
                .put("type", "conway")
                .put("frameDurationMs", spec.frameDurationMs)
                .put("initialState", Base64.encodeToString(bytes, Base64.NO_WRAP))
        }
        is ProceduralSpec.ShiftingNoise -> JSONObject()
            .put("type", "noise")
            .put("frameDurationMs", spec.frameDurationMs)
            .put("seed", spec.seed)
            .put("speed", spec.speed.toDouble())
            .put("scale", spec.scale.toDouble())
            .put("detail", spec.detail.toDouble())
        is ProceduralSpec.LavaLamp -> JSONObject()
            .put("type", "lava")
            .put("frameDurationMs", spec.frameDurationMs)
            .put("seed", spec.seed)
            .put("speed", spec.speed.toDouble())
            .put("blobCount", spec.blobCount)
            .put("softness", spec.softness.toDouble())
        is ProceduralSpec.OrganicBloom -> JSONObject()
            .put("type", "organic-bloom")
            .put("frameDurationMs", spec.frameDurationMs)
            .put("seed", spec.seed)
            .put("feed", spec.feed.toDouble())
            .put("kill", spec.kill.toDouble())
    }

    private fun proceduralFromJson(json: JSONObject?): ProceduralSpec? {
        json ?: return null
        return when (json.getString("type")) {
            "conway" -> {
                val bytes = Base64.decode(json.getString("initialState"), Base64.DEFAULT)
                require(bytes.size == PIXEL_COUNT) { "Invalid Conway seed dimensions" }
                ProceduralSpec.ConwayLife(
                    frameDurationMs = json.optInt("frameDurationMs", 140).coerceIn(67, 2_000),
                    initialState = IntArray(PIXEL_COUNT) { if (bytes[it].toInt() != 0) 255 else 0 },
                ).normalized()
            }
            "noise" -> ProceduralSpec.ShiftingNoise(
                frameDurationMs = json.optInt("frameDurationMs", 67).coerceIn(67, 500),
                seed = json.optLong("seed", 0x51F7L),
                speed = json.optDouble("speed", 1.0).toFloat(),
                scale = json.optDouble("scale", 1.0).toFloat(),
                detail = json.optDouble("detail", 0.35).toFloat(),
            ).normalized()
            "lava" -> ProceduralSpec.LavaLamp(
                frameDurationMs = json.optInt("frameDurationMs", 67).coerceIn(67, 500),
                seed = json.optLong("seed", 0x1A7A1A7AL),
                speed = json.optDouble("speed", 1.0).toFloat(),
                blobCount = json.optInt("blobCount", 4),
                softness = json.optDouble("softness", 0.18).toFloat(),
            ).normalized()
            "organic-bloom" -> ProceduralSpec.OrganicBloom(
                frameDurationMs = json.optInt("frameDurationMs", 75).coerceIn(67, 500),
                seed = json.optLong("seed", 0xB1005L),
                feed = json.optDouble("feed", 0.055).toFloat(),
                kill = json.optDouble("kill", 0.062).toFloat(),
            ).normalized()
            else -> error("Unknown procedural effect type")
        }
    }

    private fun effectFromJson(json: JSONObject): PlayboxEffect {
        val framesJson = json.getJSONArray("frames")
        require(framesJson.length() in 1..MAX_EFFECT_FRAMES) { "Invalid frame count" }
        val frames = buildList {
            for (index in 0 until framesJson.length()) {
                val source = framesJson.getJSONObject(index)
                val bytes = Base64.decode(source.getString("pixels"), Base64.DEFAULT)
                require(bytes.size == PIXEL_COUNT) { "Invalid frame dimensions" }
                add(EffectFrame(
                    pixels = IntArray(PIXEL_COUNT) { bytes[it].toInt() and 0xff },
                    durationMs = source.getInt("durationMs").coerceIn(33, 5_000),
                ).normalized())
            }
        }
        return PlayboxEffect(
            id = json.getString("id"),
            name = json.getString("name").take(60),
            description = json.optString("description").take(240),
            frames = frames,
            loopMode = runCatching { LoopMode.valueOf(json.optString("loopMode")) }.getOrDefault(LoopMode.LOOP),
            builtIn = false,
            procedural = proceduralFromJson(json.optJSONObject("procedural")),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
        )
    }

    private companion object {
        const val KEY_ACTIVE_EFFECT = "active_effect"
    }
}
