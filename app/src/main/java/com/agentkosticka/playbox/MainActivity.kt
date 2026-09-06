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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import androidx.compose.runtime.SideEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
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
import com.agentkosticka.playbox.ui.NothingDotFont
import com.agentkosticka.playbox.ui.NothingRed
import com.agentkosticka.playbox.ui.PlayboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private lateinit var playboxApplication: PlayboxApplication
    private lateinit var playboxViewModel: PlayboxViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        playboxApplication = application as PlayboxApplication
        playboxViewModel = ViewModelProvider(this)[PlayboxViewModel::class.java]
        setContent {
            PlayboxTheme {
                PlayboxApp(playboxApplication.repository, playboxApplication.glyphClient, playboxViewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        playboxApplication.glyphClient.connect()
    }

    override fun onStop() {
        playboxApplication.glyphClient.close()
        super.onStop()
    }
}

@Composable
private fun PlayboxApp(repository: EffectRepository, glyphClient: GlyphMatrixClient, viewModel: PlayboxViewModel) {
    val effects by repository.effects.collectAsState()
    val connection by glyphClient.state.collectAsState()
    val editing = viewModel.editorDraft
    var createDialog by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var exportEffect by remember { mutableStateOf<PlayboxEffect?>(null) }
    var importProgress by remember { mutableStateOf<Float?>(null) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val resolver = context.contentResolver
    val filenameFallback = stringResource(R.string.effect_filename_fallback)
    var section by rememberSaveable {
        mutableStateOf(if ((context as? MainActivity)?.intent?.getBooleanExtra("open_widgets", false) == true) "Widgets" else "Matrix")
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            runCatching { ImageImporter.import(resolver, uris) }
                .onSuccess(viewModel::beginEdit)
                .onFailure { message = it.message ?: context.getString(R.string.error_import_images) }
        }
    }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { repository.importEffect(resolver, uri) }
                .onSuccess(viewModel::beginEdit)
                .onFailure { message = it.message ?: context.getString(R.string.error_invalid_playbox_effect) }
        }
    }
    val video = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            importProgress = 0f
            try {
                val effect = VideoImporter.import(context, uri) { progress ->
                    mainHandler.post { importProgress = progress }
                }
                viewModel.beginEdit(effect)
            } catch (error: Throwable) {
                message = error.message ?: context.getString(R.string.error_import_video)
            } finally {
                importProgress = null
            }
        }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val effect = exportEffect
        if (uri != null && effect != null) scope.launch {
            runCatching { repository.exportEffect(effect, resolver, uri) }
                .onSuccess { message = context.getString(R.string.message_exported, effect.name) }
                .onFailure { message = it.message ?: context.getString(R.string.error_export) }
        }
    }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); message = null }
    }

    if (editing != null) {
        EditorScreen(
            initial = editing,
            connection = connection,
            glyphClient = glyphClient,
            onDraftChanged = viewModel::updateDraft,
            onBack = { saved ->
                viewModel.updateDraft(saved)
                scope.launch {
                    runCatching { repository.save(saved) }
                        .onSuccess { viewModel.clearEditor() }
                        .onFailure { message = it.message ?: context.getString(R.string.error_save_effect) }
                }
            },
            onDiscard = viewModel::clearEditor,
            onExport = { effect ->
                exportEffect = effect
                exportFile.launch("${safeFileName(effect.name, filenameFallback)}.playbox")
            },
        )
    } else {
        HomeScreen(
            repository = repository,
            section = section,
            onSection = { section = it },
            effects = effects,
            connection = connection,
            glyphClient = glyphClient,
            snackbar = snackbar,
            onCreate = { createDialog = true },
            onEdit = { effect -> viewModel.beginEdit(if (effect.builtIn) effect.editableCopy() else effect) },
            onNewProfile = { effect -> viewModel.beginEdit(effect.editableCopy(context.getString(R.string.profile_name_format, effect.name))) },
            onActivate = { effect ->
                repository.setActiveEffect(effect.id)
                section = "AOD"
            },
            onDelete = { id ->
                scope.launch {
                    runCatching { repository.delete(id) }
                        .onFailure { message = it.message ?: context.getString(R.string.error_delete_effect) }
                }
            },
            onImport = { importFile.launch(arrayOf("application/zip", "application/octet-stream")) },
        )
    }

    if (createDialog) {
        AlertDialog(
            onDismissRequest = { createDialog = false },
            title = { Text(stringResource(R.string.create_effect_title), fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        createDialog = false
                        viewModel.beginEdit(blankEffect())
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.blank_static)) }
                    OutlinedButton(onClick = {
                        createDialog = false
                        viewModel.beginEdit(blankEffect(animated = true))
                    }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.blank_animation)) }
                    OutlinedButton(onClick = {
                        createDialog = false
                        photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.images))
                    }
                    OutlinedButton(onClick = {
                        createDialog = false
                        video.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.video_up_to_seconds))
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { createDialog = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    importProgress?.let { progress ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.importing_video_title), fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.importing_video_description))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text(stringResource(R.string.percent_format, (progress * 100).roundToInt()), color = Muted)
                }
            },
            confirmButton = {},
        )
    }
}

private fun safeFileName(name: String, fallback: String) =
    name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(48).ifBlank { fallback }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    repository: EffectRepository,
    section: String,
    onSection: (String) -> Unit,
    effects: List<PlayboxEffect>,
    connection: GlyphConnectionState,
    glyphClient: GlyphMatrixClient,
    snackbar: SnackbarHostState,
    onCreate: () -> Unit,
    onEdit: (PlayboxEffect) -> Unit,
    onNewProfile: (PlayboxEffect) -> Unit,
    onActivate: (PlayboxEffect) -> Unit,
    onDelete: (String) -> Unit,
    onImport: () -> Unit,
) {
    var engine by rememberSaveable { mutableStateOf<String?>(null) }
    val engines = effects.filter { it.builtIn && it.procedural != null }
    val selectedEngine = engines.firstOrNull { it.id == engine }
    val visibleEffects = when (section) {
        "Matrix" -> effects.filter { it.procedural == null }
        "Procedural" -> if (selectedEngine == null) emptyList() else effects.filter {
            it.procedural != null && it.procedural::class == selectedEngine.procedural!!::class
        }.sortedByDescending { it.builtIn }
        else -> emptyList()
    }
    BackHandler(enabled = section == "Procedural" && engine != null) { engine = null }
    var playingId by remember { mutableStateOf<String?>(null) }
    var playingPixels by remember { mutableStateOf<IntArray?>(null) }
    LaunchedEffect(section) {
        playingId = null
        playingPixels = null
        glyphClient.stopDisplay()
    }
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
    DisposableEffect(Unit) { onDispose { glyphClient.stopDisplay() } }

    val navItems = listOf(
        Triple("Matrix", R.string.section_matrix, Icons.Default.GridView),
        Triple("Procedural", R.string.section_procedural, Icons.Default.AutoAwesome),
        Triple("Widgets", R.string.section_widgets, Icons.Default.Widgets),
        Triple("AOD", R.string.section_aod, Icons.Default.Lightbulb),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black),
                title = { Text(stringResource(R.string.app_name).uppercase(), fontFamily = NothingDotFont.family, fontWeight = FontWeight.Bold) },
                actions = {
                    if (section == "Matrix" || section == "Procedural") {
                        IconButton(onClick = onImport) { Icon(Icons.Default.Upload, stringResource(R.string.import_effect_cd)) }
                    }
                },
            )
        },
        floatingActionButton = {
            if (section == "Matrix") {
                FloatingActionButton(onClick = onCreate, containerColor = NothingRed, contentColor = Color.White) {
                    Icon(Icons.Default.Add, stringResource(R.string.create_effect_cd))
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                navItems.forEach { (id, labelRes, icon) ->
                    NavigationBarItem(
                        selected = section == id,
                        onClick = {
                            playingId = null
                            onSection(id)
                        },
                        icon = { Icon(icon, null) },
                        label = { Text(stringResource(labelRes)) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                val heading = when (section) {
                    "Procedural" -> selectedEngine?.name ?: stringResource(R.string.home_title_procedural)
                    "Widgets" -> stringResource(R.string.home_title_widgets)
                    "AOD" -> stringResource(R.string.home_title_aod)
                    else -> stringResource(R.string.home_title_matrix)
                }
                val subtitle = when (section) {
                    "Procedural" -> stringResource(R.string.home_subtitle_procedural)
                    "Widgets" -> stringResource(R.string.home_subtitle_widgets)
                    "AOD" -> stringResource(R.string.home_subtitle_aod)
                    else -> stringResource(R.string.home_subtitle_matrix)
                }
                Text(heading, fontFamily = NothingDotFont.family, fontSize = 25.sp, lineHeight = 29.sp)
                Spacer(Modifier.height(4.dp))
                Text(subtitle, color = Muted)
            }
            if (section == "Widgets") item { WidgetsScreen() }
            if (section == "AOD") item { AodScreen(repository, glyphClient) }
            if (section == "Procedural" && selectedEngine == null) {
                items(engines, key = { "engine-${it.id}" }) { effect ->
                    Card(onClick = { engine = effect.id }, shape = RoundedCornerShape(24.dp)) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(104.dp).clip(CircleShape).background(Color.Black).padding(7.dp)) {
                                MatrixDisplay(effect.frames.first().pixels, Modifier.fillMaxSize())
                            }
                            Column(Modifier.padding(start = 20.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(effect.name, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                                Text(effect.description, color = Muted, fontSize = 12.sp)
                                val count = effects.count {
                                    !it.builtIn && it.procedural != null && it.procedural::class == effect.procedural!!::class
                                }
                                Text(stringResource(R.string.profiles_count, count), color = NothingRed, fontSize = 11.sp)
                                Text(stringResource(R.string.open_profiles), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            if (section == "Procedural" && selectedEngine != null) item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { playingId = null; engine = null }) { Text(stringResource(R.string.back_to_engines)) }
                    Button(onClick = { onNewProfile(selectedEngine) }) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.new_profile))
                    }
                }
                Text(stringResource(R.string.profile_intro), color = Muted, fontSize = 12.sp)
            }
            items(visibleEffects, key = { it.id }) { effect ->
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
                    onEdit = if (effect.procedural != null && effect.builtIn) onNewProfile else onEdit,
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
                    Text(
                        effect.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (effect.builtIn) Text(stringResource(R.string.built_in), color = NothingRed, fontSize = 9.sp)
                }
                Text(effect.description, color = Muted, fontSize = 12.sp, maxLines = 2)
                Text(
                    if (effect.procedural != null) stringResource(R.string.live_procedural)
                    else pluralStringResource(R.plurals.frame_count, effect.frames.size, effect.frames.size),
                    fontSize = 11.sp,
                    color = if (effect.procedural != null) NothingRed else Color.Unspecified,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = onPlay, contentPadding = PaddingValues(horizontal = 12.dp)) {
                        Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                        Text(stringResource(if (isPlaying) R.string.stop else R.string.play))
                    }
                    TextButton(onClick = { onEdit(effect) }) {
                        Text(
                            stringResource(
                                when {
                                    effect.procedural != null && effect.builtIn -> R.string.new_profile
                                    effect.builtIn -> R.string.copy_and_edit
                                    else -> R.string.edit
                                },
                            ),
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onActivate(effect) }) {
                        Icon(Icons.Default.Lightbulb, null)
                        Text(stringResource(R.string.use_as_aod))
                    }
                    if (!effect.builtIn) {
                        IconButton(onClick = { onDelete(effect.id) }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete_cd))
                        }
                    }
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
    onDraftChanged: (PlayboxEffect) -> Unit,
    onBack: (PlayboxEffect) -> Unit,
    onDiscard: () -> Unit,
    onExport: (PlayboxEffect) -> Unit,
) {
    if (initial.procedural != null) {
        ProceduralEditorScreen(initial, connection, glyphClient, onDraftChanged, onBack, onDiscard, onExport)
        return
    }

    var draft by remember(initial.id) {
        mutableStateOf(initial.copy(frames = initial.frames.map { it.copy(pixels = it.pixels.copyOf()) }))
    }
    var frameIndex by remember { mutableIntStateOf(0) }
    var intensity by rememberSaveable { mutableIntStateOf(255) }
    var playing by remember { mutableStateOf(false) }
    var live by remember { mutableStateOf(false) }
    val undoByFrame = remember(initial.id) { mutableMapOf<Int, MutableList<IntArray>>() }
    val redoByFrame = remember(initial.id) { mutableMapOf<Int, MutableList<IntArray>>() }
    var historyVersion by remember(initial.id) { mutableIntStateOf(0) }
    var strokeStart by remember(frameIndex) { mutableStateOf<IntArray?>(null) }

    SideEffect { onDraftChanged(draft) }

    fun undoStack(): MutableList<IntArray> = undoByFrame.getOrPut(frameIndex) { mutableListOf() }
    fun redoStack(): MutableList<IntArray> = redoByFrame.getOrPut(frameIndex) { mutableListOf() }
    fun clearFrameHistory() {
        undoByFrame.clear()
        redoByFrame.clear()
        historyVersion++
    }
    fun replaceFrame(frame: EffectFrame) {
        draft = draft.copy(
            frames = draft.frames.toMutableList().also { it[frameIndex] = frame },
            updatedAt = System.currentTimeMillis(),
        )
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
    fun finish() {
        glyphClient.stopDisplay()
        onBack(draft)
    }
    fun discard() {
        glyphClient.stopDisplay()
        onDiscard()
    }

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
                title = { Text(stringResource(R.string.pixel_lab_title), fontFamily = FontFamily.Monospace) },
                navigationIcon = {
                    IconButton(onClick = ::finish) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.save_and_back_cd))
                    }
                },
                actions = {
                    IconButton(onClick = { onExport(draft) }) { Icon(Icons.Default.Download, stringResource(R.string.export_cd)) }
                    TextButton(onClick = ::discard) { Text(stringResource(R.string.discard)) }
                    TextButton(onClick = ::finish) { Text(stringResource(R.string.save)) }
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
                    label = { Text(stringResource(R.string.effect_name)) },
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
                        Icon(
                            if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            stringResource(if (playing) R.string.stop_cd else R.string.play_cd),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    FilterChip(
                        selected = live,
                        onClick = {
                            live = !live
                            if (!live) glyphClient.stopDisplay()
                        },
                        label = { Text(stringResource(if (live) R.string.live_matrix else R.string.simulator)) },
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
                Text(stringResource(R.string.pixel_intensity, intensity), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
                Text(
                    stringResource(R.string.frames_count_max, draft.frames.size, MAX_EFFECT_FRAMES),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
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
                    ) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.blank_frame))
                    }
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
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Text(stringResource(R.string.duplicate_frame))
                    }
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
                    }) { Icon(Icons.Default.Delete, stringResource(R.string.delete_frame_cd)) }
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
                    stringResource(R.string.fps_integer, fpsTenths / 10)
                } else {
                    stringResource(R.string.fps_decimal, fpsTenths / 10, fpsTenths % 10)
                }
                Text(stringResource(R.string.frame_time, duration, fpsLabel), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
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
                    Text(stringResource(R.string.fast), color = Muted, fontSize = 10.sp)
                    Text(stringResource(R.string.slow), color = Muted, fontSize = 10.sp)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { commitPixels(IntArray(draft.frames[frameIndex].pixels.size)) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.clear)) }
                    OutlinedButton(
                        onClick = { commitPixels(draft.frames[frameIndex].pixels.map { if (it == 0) 255 else 255 - it }.toIntArray()) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.invert)) }
                    OutlinedButton(
                        onClick = { commitPixels(IntArray(draft.frames[frameIndex].pixels.size) { intensity }) },
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.fill)) }
                }
            }
            item {
                Text(stringResource(R.string.loop), fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LoopMode.entries.forEach { mode ->
                        val label = stringResource(
                            when (mode) {
                                LoopMode.LOOP -> R.string.loop_mode_loop
                                LoopMode.PING_PONG -> R.string.loop_mode_ping_pong
                                LoopMode.HOLD -> R.string.loop_mode_hold
                            },
                        )
                        FilterChip(
                            selected = draft.loopMode == mode,
                            onClick = { draft = draft.copy(loopMode = mode) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
    }
}
