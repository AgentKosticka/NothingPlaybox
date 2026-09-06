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
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class EffectRepository(context: Context) {
    private val appContext = context.applicationContext
    private val directory = appContext.filesDir.resolve("effects-v2").apply { mkdirs() }
    private val legacyStorage = AtomicFile(appContext.filesDir.resolve("effects-v1.json"))
    private val preferences = appContext.getSharedPreferences("playbox", Context.MODE_PRIVATE)
    private val mutationMutex = Mutex()
    private var userEffects: MutableList<PlayboxEffect> = loadUsers().toMutableList()
    private val _effects = MutableStateFlow(mergedEffects())

    val effects: StateFlow<List<PlayboxEffect>> = _effects.asStateFlow()
    private val _activeId = MutableStateFlow(preferences.getString(KEY_ACTIVE_EFFECT, null))
    val activeId = _activeId.asStateFlow()
    val activeEffectId: String?
        get() = preferences.getString(KEY_ACTIVE_EFFECT, null)

    fun find(id: String): PlayboxEffect? = _effects.value.firstOrNull { it.id == id }

    suspend fun save(effect: PlayboxEffect): PlayboxEffect = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
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
            require(index >= 0 || userEffects.size < MAX_USER_EFFECTS) {
                "Your library can contain at most $MAX_USER_EFFECTS saved effects"
            }
            persistEffect(normalized)
            if (index >= 0) userEffects[index] = normalized else userEffects.add(0, normalized)
            _effects.value = mergedEffects()
            normalized
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        mutationMutex.withLock {
            val removed = userEffects.removeAll { it.id == id }
            if (!removed) return@withLock
            effectFile(id).delete()
            if (activeEffectId == id) setActiveEffect(EffectCatalog.builtIns.first().id)
            _effects.value = mergedEffects()
        }
    }

    fun setActiveEffect(id: String) {
        preferences.edit { putString(KEY_ACTIVE_EFFECT, id) }
        _activeId.value = id
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

    /** Decode into a transient draft. Importing no longer mutates the library until the user saves. */
    suspend fun importEffect(resolver: ContentResolver, uri: Uri): PlayboxEffect = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use(::readPlayboxManifest)
            ?: error("Unable to read effect")
        val manifest = JSONObject(String(bytes, Charsets.UTF_8))
        require(manifest.optInt("schema", 0) == PLAYBOX_SCHEMA_VERSION) { "Unsupported Playbox effect version" }
        effectFromJson(manifest).editableCopy(manifest.optString("name", "Imported effect").take(60))
    }

    private fun mergedEffects() = userEffects.sortedByDescending { it.updatedAt } + EffectCatalog.builtIns

    private fun loadUsers(): List<PlayboxEffect> {
        val current = loadV2Users().toMutableList()
        if (!legacyStorage.baseFile.exists()) return current

        // Migration is deliberately resumable. A partially migrated v1 file remains the source of
        // truth for anything that has not made it safely into v2 yet; it is deleted only after every
        // legacy entry was parsed and represented in v2.
        val legacy = loadLegacyUsers() ?: return current
        var complete = legacy.complete
        val migratedById = current.associateByTo(linkedMapOf()) { it.id }

        legacy.effects.forEach { effect ->
            if (effect.id in migratedById) return@forEach
            if (migratedById.size >= MAX_USER_EFFECTS) {
                complete = false
                return@forEach
            }
            if (runCatching { persistEffect(effect) }.isSuccess) {
                migratedById[effect.id] = effect
            } else {
                complete = false
            }
        }

        if (complete) legacyStorage.delete()
        return migratedById.values.sortedByDescending { it.updatedAt }.take(MAX_USER_EFFECTS)
    }

    private fun loadV2Users(): List<PlayboxEffect> = directory.listFiles()
        .orEmpty()
        .asSequence()
        .filter { it.isFile && it.extension == "json" && it.length() in 1..MAX_EFFECT_FILE_BYTES.toLong() }
        .mapNotNull { file ->
            runCatching { effectFromJson(JSONObject(file.readText(Charsets.UTF_8))) }.getOrNull()
        }
        .sortedByDescending { it.updatedAt }
        .take(MAX_USER_EFFECTS)
        .toList()

    private data class LegacyLoad(val effects: List<PlayboxEffect>, val complete: Boolean)

    private fun loadLegacyUsers(): LegacyLoad? = runCatching {
        val legacySize = legacyStorage.baseFile.length()
        require(legacySize in 1..MAX_LIBRARY_BYTES) { "Legacy effect library is too large" }
        val root = legacyStorage.openRead().bufferedReader().use { JSONObject(it.readText()) }
        require(root.optInt("schema", 0) == PLAYBOX_SCHEMA_VERSION) { "Unsupported legacy effect schema" }
        val array = root.getJSONArray("effects")
        var complete = array.length() <= MAX_USER_EFFECTS
        val effects = buildList {
            for (index in 0 until minOf(array.length(), MAX_USER_EFFECTS)) {
                val parsed = runCatching { effectFromJson(array.getJSONObject(index)) }.getOrNull()
                if (parsed == null) complete = false else add(parsed)
            }
        }
        LegacyLoad(effects, complete)
    }.getOrNull()

    private fun persistEffect(effect: PlayboxEffect) {
        val payload = effectToJson(effect).toString().toByteArray(Charsets.UTF_8)
        require(payload.size <= MAX_EFFECT_FILE_BYTES) {
            "This effect is too large to save (${payload.size / 1024} KiB; max ${MAX_EFFECT_FILE_BYTES / 1024} KiB)"
        }

        val target = effectFile(effect.id)
        val currentTotal = directory.listFiles().orEmpty().sumOf { file ->
            if (file.absolutePath == target.absolutePath) 0L else file.length()
        }
        require(currentTotal + payload.size <= MAX_LIBRARY_BYTES) {
            "Saved effects have reached the ${MAX_LIBRARY_BYTES / 1_000_000} MB library limit"
        }

        val storage = AtomicFile(target)
        val output = storage.startWrite()
        try {
            output.write(payload)
            output.flush()
            storage.finishWrite(output)
        } catch (error: Throwable) {
            storage.failWrite(output)
            throw error
        }
    }

    private fun effectFile(id: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(id.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return directory.resolve("$name.json")
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
        is ProceduralSpec.RippleField -> JSONObject().put("type", "ripple-field")
            .put("frameDurationMs", spec.frameDurationMs).put("speed", spec.speed.toDouble())
            .put("wavelength", spec.wavelength.toDouble()).put("sources", spec.sources)
        is ProceduralSpec.Starfield -> JSONObject().put("type", "starfield")
            .put("frameDurationMs", spec.frameDurationMs).put("speed", spec.speed.toDouble())
            .put("seed", spec.seed).put("stars", spec.stars).put("trails", spec.trails.toDouble())
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
            "ripple-field" -> ProceduralSpec.RippleField(
                frameDurationMs = json.optInt("frameDurationMs", 67), speed = json.optDouble("speed", 1.0).toFloat(),
                wavelength = json.optDouble("wavelength", 3.0).toFloat(), sources = json.optInt("sources", 3),
            ).normalized()
            "starfield" -> ProceduralSpec.Starfield(
                frameDurationMs = json.optInt("frameDurationMs", 67), speed = json.optDouble("speed", 1.0).toFloat(),
                seed = json.optLong("seed", 731L), stars = json.optInt("stars", 24), trails = json.optDouble("trails", .35).toFloat(),
            ).normalized()
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

    internal companion object {
        const val MAX_USER_EFFECTS = 100
        const val MAX_EFFECT_FILE_BYTES = 512_000
        const val MAX_LIBRARY_BYTES = 16_000_000L
        private const val KEY_ACTIVE_EFFECT = "active_effect"
    }
}
