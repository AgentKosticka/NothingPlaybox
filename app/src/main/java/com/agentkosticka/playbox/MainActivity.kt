package com.agentkosticka.playbox

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agentkosticka.playbox.data.EffectRepository
import com.agentkosticka.playbox.data.ImageImporter
import com.agentkosticka.playbox.data.ProceduralEffectRuntime
import com.agentkosticka.playbox.data.VideoImporter
import com.agentkosticka.playbox.matrix.GlyphConnectionState
import com.agentkosticka.playbox.matrix.GlyphMatrixClient
import com.agentkosticka.playbox.model.EffectFrame
import com.agentkosticka.playbox.model.LoopMode
import com.agentkosticka.playbox.model.MAX_EFFECT_FRAMES
import com.agentkosticka.playbox.model.PlayboxEffect
import com.agentkosticka.playbox.model.blankEffect
import com.agentkosticka.playbox.model.frameIndexAt
import com.agentkosticka.playbox.ui.MatrixDisplay
import com.agentkosticka.playbox.ui.Muted
import com.agentkosticka.playbox.ui.NothingRed
import com.agentkosticka.playbox.ui.PlayboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as PlayboxApplication
        setContent {
            PlayboxTheme {
                PlayboxApp(app.repository, app.glyphClient)
            }
        }
    }
}

@Composable
private fun PlayboxApp(repository: EffectRepository, glyphClient: GlyphMatrixClient) {
    val effects by repository.effects.collectAsState()
    val connection by glyphClient.state.collectAsState()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var createDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportEffect by remember { mutableStateOf<PlayboxEffect?>(null) }
    var importProgress by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val resolver = androidx.compose.ui.platform.LocalContext.current.contentResolver
    val context = androidx.compose.ui.platform.LocalContext.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            runCatching { ImageImporter.import(resolver, uris) }
                .onSuccess { editingId = repository.save(it).id }
                .onFailure { message = it.message ?: "Unable to import images" }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { repository.importEffect(resolver, uri) }
                .onSuccess { editingId = it.id }
                .onFailure { message = it.message ?: "Invalid Playbox effect" }
        }
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importProgress = 0f
            try {
                val effect = VideoImporter.import(context, uri) { progress ->
                    mainHandler.post { importProgress = progress }
                }
                editingId = repository.save(effect).id
            } catch (error: Throwable) {
                message = error.message ?: "Unable to import video"
            } finally {
                importProgress = null
            }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val effect = exportEffect
        if (uri != null && effect != null) scope.launch {
            runCatching { repository.exportEffect(effect, resolver, uri) }
                .onSuccess { message = "Exported ${effect.name}" }
                .onFailure { message = it.message ?: "Export failed" }
        }
    }

    LaunchedEffect(Unit) { glyphClient.connect() }
    DisposableEffect(Unit) { onDispose { glyphClient.close() } }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    val editing = editingId?.let(repository::find)
    if (editing != null) {
        EditorScreen(
            initial = editing,
            connection = connection,
            glyphClient = glyphClient,
            onBack = { saved -> repository.save(saved); editingId = null },
            onExport = { effect -> exportEffect = effect; exportFile.launch("${safeFileName(effect.name)}.playbox") },
        )
    } else {
        HomeScreen(
            effects = effects,
            connection = connection,
            glyphClient = glyphClient,
            snackbar = snackbar,
            onCreate = { createDialog = true },
            onEdit = { effect -> editingId = repository.save(if (effect.builtIn) effect.editableCopy() else effect).id },
            onActivate = { effect ->
                repository.setActiveEffect(effect.id)
                glyphClient.openToyManager().onFailure {
                    message = "Selected ${effect.name}. Enable Nothing Playbox in Settings → Glyph Interface → Flip to Glyph → Always-on Glyph Toy."
                }
            },
            onDelete = repository::delete,
            onImport = { importFile.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text("CREATE EFFECT", fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        createDialog = false
                        editingId = repository.save(blankEffect()).id
                    }, modifier = Modifier.fillMaxWidth()) { Text("Blank static") }
                    OutlinedButton(onClick = {
                        createDialog = false
                        editingId = repository.save(blankEffect(animated = true)).id
                    }, modifier = Modifier.fillMaxWidth()) { Text("Blank animation") }
                    OutlinedButton(onClick = {
                        createDialog = false
                        photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null); Spacer(Modifier.width(8.dp)); Text("Image(s)")
                    }
                    OutlinedButton(onClick = {
                        createDialog = false
                        video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Video (up to 60 seconds)")
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text("Cancel") } },
        )
    }

    importProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("IMPORTING VIDEO", fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Turning video into 13×13 intensity frames…")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).roundToInt()}%", color = Muted)
                }
            },
            confirmButton = {},
        )
    }
}

private fun safeFileName(name: String) = name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { "effect" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    effects: List<PlayboxEffect>,
    connection: GlyphConnectionState,
    glyphClient: GlyphMatrixClient,
    snackbar: SnackbarHostState,
    onCreate: () -> Unit,
    onEdit: (PlayboxEffect) -> Unit,
    onActivate: (PlayboxEffect) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
) {
    var playingId by remember { mutableStateOf<String?>(null) }
    var playingPixels by remember { mutableStateOf<IntArray?>(null) }
    val playingEffect = playingId?.let { id -> effects.firstOrNull { it.id == id } }

    LaunchedEffect(playingEffect, connection) {
        val effect = playingEffect
        if (effect == null) {
            playingId = null
            playingPixels = null
            glyphClient.stopDisplay()
            return@LaunchedEffect
        }
        val runtime = effect.procedural?.let { ProceduralEffectRuntime(effect) }
        val started = android.os.SystemClock.elapsedRealtime()
        while (true) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - started
            val frame = runtime?.frameAt(elapsed) ?: effect.frames[effect.frameIndexAt(elapsed)]
            playingPixels = frame.pixels
            if (connection == GlyphConnectionState.Ready) glyphClient.showFrame(frame.pixels)
            delay(frame.durationMs.toLong())
        }
    }
    DisposableEffect(Unit) {
        onDispose { glyphClient.stopDisplay() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = {
                    Column {
                        Text("NOTHING PLAYBOX", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(connectionLabel(connection), color = connectionColor(connection), fontSize = 11.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onImport) { Icon(Icons.Default.Upload, "Import effect") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate, containerColor = NothingRed, contentColor = Color.White) {
                Icon(Icons.Default.Add, "Create effect")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("WHAT CAN THE PHONE DO?", fontFamily = FontFamily.Monospace, fontSize = 25.sp, lineHeight = 29.sp)
                Spacer(Modifier.height(4.dp))
                Text("137 lights. Every one under your control.", color = Muted)
            }
            items(effects, key = { it.id }) { effect ->
                EffectCard(
                    effect = effect,
                    previewPixels = if (effect.id == playingId) playingPixels else null,
                    isPlaying = effect.id == playingId,
                    onPlay = {
                        if (playingId == effect.id) {
                            playingId = null
                            playingPixels = null
                            glyphClient.stopDisplay()
                        } else {
                            playingPixels = null
                            playingId = effect.id
                        }
                    },
                    onEdit = onEdit,
                    onActivate = onActivate,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun EffectCard(
    effect: PlayboxEffect,
    previewPixels: IntArray?,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onEdit: (PlayboxEffect) -> Unit,
    onActivate: (PlayboxEffect) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(104.dp).clip(CircleShape).background(Color.Black).padding(7.dp)) {
                MatrixDisplay(previewPixels ?: effect.frames.first().pixels, Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(effect.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (effect.builtIn) Text("BUILT-IN", color = NothingRed, fontSize = 9.sp)
                }
                Text(effect.description, color = Muted, fontSize = 12.sp, maxLines = 2)
                Text(
                    if (effect.procedural != null) "LIVE • PROCEDURAL" else "${effect.frames.size} frame${if (effect.frames.size == 1) "" else "s"}",
                    fontSize = 11.sp,
                    color = if (effect.procedural != null) NothingRed else Color.Unspecified,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPlay, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Text(if (isPlaying) " STOP" else " PLAY")
                    }
                    TextButton(onClick = { onEdit(effect) }) { Text(if (effect.builtIn) "COPY & EDIT" else "EDIT") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onActivate(effect) }) {
                        Icon(Icons.Default.Lightbulb, null)
                        Text(" USE AS AOD")
                    }
                    if (!effect.builtIn) IconButton(onClick = { onDelete(effect.id) }) { Icon(Icons.Default.Delete, "Delete") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    initial: PlayboxEffect,
    connection: GlyphConnectionState,
    glyphClient: GlyphMatrixClient,
    onBack: (PlayboxEffect) -> Unit,
    onExport: (PlayboxEffect) -> Unit,
) {
    if (initial.procedural != null) {
        ProceduralEditorScreen(initial, connection, glyphClient, onBack, onExport)
        return
    }

    var draft by remember(initial.id) { mutableStateOf(initial.copy(frames = initial.frames.map { it.copy(pixels = it.pixels.copyOf()) })) }
    var frameIndex by remember { mutableIntStateOf(0) }
    var intensity by rememberSaveable { mutableIntStateOf(255) }
    var playing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    val undoByFrame = remember(initial.id) { mutableMapOf<Int, MutableList<IntArray>>() }
    val redoByFrame = remember(initial.id) { mutableMapOf<Int, MutableList<IntArray>>() }
    var historyVersion by remember(initial.id) { mutableIntStateOf(0) }
    var strokeStart by remember(frameIndex) { mutableStateOf<IntArray?>(null) }

    fun undoStack(): MutableList<IntArray> = undoByFrame.getOrPut(frameIndex) { mutableListOf() }
    fun redoStack(): MutableList<IntArray> = redoByFrame.getOrPut(frameIndex) { mutableListOf() }
    fun clearFrameHistory() {
        undoByFrame.clear()
        redoByFrame.clear()
        historyVersion++
    }
    fun replaceFrame(frame: EffectFrame) {
        draft = draft.copy(frames = draft.frames.toMutableList().also { it[frameIndex] = frame }, updatedAt = System.currentTimeMillis())
    }
    fun pushUndo(snapshot: IntArray) {
        undoStack().add(snapshot.copyOf())
        if (undoStack().size > 50) undoStack().removeAt(0)
        redoStack().clear()
        historyVersion++
    }
    fun commitPixels(next: IntArray, recordUndo: Boolean = true) {
        val current = draft.frames[frameIndex].pixels
        if (current.contentEquals(next)) return
        if (recordUndo) pushUndo(current)
        replaceFrame(draft.frames[frameIndex].copy(pixels = next).normalized())
        if (!recordUndo) historyVersion++
    }
    fun beginStroke() {
        if (strokeStart == null) strokeStart = draft.frames[frameIndex].pixels.copyOf()
    }
    fun endStroke() {
        val snapshot = strokeStart ?: return
        strokeStart = null
        if (!snapshot.contentEquals(draft.frames[frameIndex].pixels)) pushUndo(snapshot)
    }
    fun finish() { glyphClient.stopDisplay(); onBack(draft) }

    BackHandler { finish() }
    DisposableEffect(Unit) { onDispose { glyphClient.stopDisplay() } }
    LaunchedEffect(live, playing, connection, frameIndex, draft.frames[frameIndex].pixels.contentHashCode()) {
        when {
            live && !playing && connection == GlyphConnectionState.Ready -> glyphClient.showFrame(draft.frames[frameIndex].pixels)
            !live && !playing -> glyphClient.stopDisplay()
        }
    }
    LaunchedEffect(playing, live, connection, draft.id, draft.updatedAt) {
        if (!playing) return@LaunchedEffect
        val startingIndex = frameIndex.coerceIn(draft.frames.indices)
        val startingOffset = draft.frames.take(startingIndex).sumOf { it.durationMs }.toLong()
        val started = android.os.SystemClock.elapsedRealtime() - startingOffset
        while (true) {
            val index = draft.frameIndexAt(android.os.SystemClock.elapsedRealtime() - started)
            frameIndex = index
            if (live && connection == GlyphConnectionState.Ready) glyphClient.showFrame(draft.frames[index].pixels)
            delay(draft.frames[index].durationMs.toLong())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = { Text("PIXEL LAB", fontFamily = FontFamily.Monospace) },
                navigationIcon = { IconButton(onClick = ::finish) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Save and back") } },
                actions = {
                    IconButton(onClick = { onExport(draft) }) { Icon(Icons.Default.Download, "Export") }
                    TextButton(onClick = ::finish) { Text("SAVE") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it.take(60)) },
                    label = { Text("Effect name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Surface(color = Color.Black, shape = CircleShape, modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                    MatrixDisplay(
                        pixels = draft.frames[frameIndex].pixels,
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        onPixel = { index ->
                            val next = draft.frames[frameIndex].pixels.copyOf()
                            next[index] = intensity
                            commitPixels(next, recordUndo = false)
                        },
                        onStrokeStart = ::beginStroke,
                        onStrokeEnd = ::endStroke,
                    )
                }
            }
            item {
                historyVersion
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledIconButton(onClick = { playing = !playing }) {
                        Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, if (playing) "Stop" else "Play")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = live,
                        onClick = {
                            live = !live
                            if (live) glyphClient.connect() else glyphClient.stopDisplay()
                        },
                        label = { Text(if (live) "LIVE MATRIX" else "SIMULATOR") },
                        leadingIcon = { Icon(Icons.Default.Lightbulb, null) },
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(enabled = undoStack().isNotEmpty(), onClick = {
                        redoStack().add(draft.frames[frameIndex].pixels.copyOf())
                        commitPixels(undoStack().removeAt(undoStack().lastIndex), recordUndo = false)
                    }) { Text("↶", fontSize = 24.sp) }
                    IconButton(enabled = redoStack().isNotEmpty(), onClick = {
                        undoStack().add(draft.frames[frameIndex].pixels.copyOf())
                        if (undoStack().size > 50) undoStack().removeAt(0)
                        commitPixels(redoStack().removeAt(redoStack().lastIndex), recordUndo = false)
                    }) { Text("↷", fontSize = 24.sp) }
                }
                if (connection is GlyphConnectionState.Connecting) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            item {
                Text("PIXEL INTENSITY  $intensity / 255", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Slider(value = intensity.toFloat(), onValueChange = { intensity = it.roundToInt() }, valueRange = 0f..255f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf(0, 64, 128, 192, 255).forEach { value ->
                        FilledIconButton(
                            onClick = { intensity = value },
                            modifier = Modifier.size(38.dp),
                            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color(value, value, value),
                                contentColor = if (value > 128) Color.Black else Color.White,
                            ),
                        ) { Text((value * 100 / 255).toString(), fontSize = 9.sp) }
                    }
                }
            }
            item { HorizontalDivider() }
            item {
                Text("FRAMES  ${draft.frames.size} / $MAX_EFFECT_FRAMES", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    itemsIndexed(draft.frames, key = { index, _ -> index }) { index, frame ->
                        Card(
                            onClick = { frameIndex = index.coerceIn(draft.frames.indices); playing = false },
                            border = if (index == frameIndex) androidx.compose.foundation.BorderStroke(2.dp, NothingRed) else null,
                            colors = CardDefaults.cardColors(containerColor = Color.Black),
                        ) {
                            Column(Modifier.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                MatrixDisplay(frame.pixels, Modifier.size(72.dp))
                                Text("${index + 1}", fontSize = 10.sp)
                            }
                        }
                    }
                }
                Row {
                    OutlinedButton(
                        enabled = draft.frames.size < MAX_EFFECT_FRAMES,
                        onClick = {
                            val frames = draft.frames.toMutableList().apply { add(frameIndex + 1, EffectFrame()) }
                            frameIndex++
                            clearFrameHistory()
                            draft = draft.copy(frames = frames, updatedAt = System.currentTimeMillis())
                        },
                    ) { Icon(Icons.Default.Add, null); Text(" Blank") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        enabled = draft.frames.size < MAX_EFFECT_FRAMES,
                        onClick = {
                            val copy = draft.frames[frameIndex].copy(pixels = draft.frames[frameIndex].pixels.copyOf())
                            val frames = draft.frames.toMutableList().apply { add(frameIndex + 1, copy) }
                            frameIndex++
                            clearFrameHistory()
                            draft = draft.copy(frames = frames, updatedAt = System.currentTimeMillis())
                        },
                    ) { Icon(Icons.Default.ContentCopy, null); Text(" Duplicate") }
                    Spacer(Modifier.weight(1f))
                    TextButton(enabled = frameIndex > 0, onClick = {
                        val frames = draft.frames.toMutableList()
                        val moved = frames.removeAt(frameIndex)
                        frames.add(frameIndex - 1, moved)
                        frameIndex--
                        clearFrameHistory()
                        draft = draft.copy(frames = frames, updatedAt = System.currentTimeMillis())
                    }) { Text("←") }
                    TextButton(enabled = frameIndex < draft.frames.lastIndex, onClick = {
                        val frames = draft.frames.toMutableList()
                        val moved = frames.removeAt(frameIndex)
                        frames.add(frameIndex + 1, moved)
                        frameIndex++
                        clearFrameHistory()
                        draft = draft.copy(frames = frames, updatedAt = System.currentTimeMillis())
                    }) { Text("→") }
                    IconButton(enabled = draft.frames.size > 1, onClick = {
                        val frames = draft.frames.toMutableList().apply { removeAt(frameIndex) }
                        frameIndex = frameIndex.coerceAtMost(frames.lastIndex)
                        clearFrameHistory()
                        draft = draft.copy(frames = frames, updatedAt = System.currentTimeMillis())
                    }) { Icon(Icons.Default.Delete, "Delete frame") }
                }
            }
            item {
                val duration = draft.frames[frameIndex].durationMs.coerceIn(33, 5_000)
                val minDuration = 33.0
                val maxDuration = 5_000.0
                val logMin = kotlin.math.ln(minDuration)
                val logSpan = kotlin.math.ln(maxDuration) - logMin
                val sliderPosition = ((kotlin.math.ln(duration.toDouble()) - logMin) / logSpan).toFloat().coerceIn(0f, 1f)
                val fpsTenths = (10_000.0 / duration).roundToInt()
                val fpsLabel = if (fpsTenths >= 100) {
                    "${fpsTenths / 10} FPS"
                } else {
                    "${fpsTenths / 10}.${fpsTenths % 10} FPS"
                }
                Text("FRAME TIME  ${duration} ms  •  $fpsLabel", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Slider(
                    value = sliderPosition,
                    onValueChange = { position ->
                        val nextDuration = kotlin.math.exp(logMin + position.coerceIn(0f, 1f) * logSpan)
                            .roundToInt()
                            .coerceIn(33, 5_000)
                        replaceFrame(draft.frames[frameIndex].copy(durationMs = nextDuration))
                    },
                    valueRange = 0f..1f,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("FAST", color = Muted, fontSize = 10.sp)
                    Text("SLOW", color = Muted, fontSize = 10.sp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        commitPixels(IntArray(draft.frames[frameIndex].pixels.size))
                    }, modifier = Modifier.weight(1f)) { Text("CLEAR") }
                    OutlinedButton(onClick = {
                        commitPixels(draft.frames[frameIndex].pixels.map { if (it == 0) 255 else 255 - it }.toIntArray())
                    }, modifier = Modifier.weight(1f)) { Text("INVERT") }
                    OutlinedButton(onClick = {
                        commitPixels(IntArray(draft.frames[frameIndex].pixels.size) { intensity })
                    }, modifier = Modifier.weight(1f)) { Text("FILL") }
                }
            }
            item {
                Text("LOOP", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoopMode.entries.forEach { mode ->
                        FilterChip(selected = draft.loopMode == mode, onClick = { draft = draft.copy(loopMode = mode) }, label = { Text(mode.name) })
                    }
                }
            }
        }
    }
}

private fun connectionLabel(state: GlyphConnectionState) = when (state) {
    GlyphConnectionState.Simulator -> "● SIMULATOR — hardware unavailable or disconnected"
    GlyphConnectionState.Connecting -> "● CONNECTING TO GLYPH MATRIX"
    GlyphConnectionState.Ready -> "● PHONE (4a) PRO MATRIX READY"
    is GlyphConnectionState.Error -> "● ${state.message.uppercase()}"
}

@Composable
private fun connectionColor(state: GlyphConnectionState) = when (state) {
    GlyphConnectionState.Ready -> Color(0xFF62D783)
    is GlyphConnectionState.Error -> MaterialTheme.colorScheme.error
    GlyphConnectionState.Connecting -> Color(0xFFFFC857)
    GlyphConnectionState.Simulator -> Muted
}
