package app.lumen.player

import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.OpenableColumns
import android.util.Rational
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import app.lumen.player.data.LibraryStore
import app.lumen.player.playback.Phase
import app.lumen.player.playback.PlayerEngine
import app.lumen.player.playback.PlayerService
import app.lumen.player.playback.QueueItem
import app.lumen.player.playback.RecentItem
import app.lumen.player.ui.HomeScreen
import app.lumen.player.ui.Ink
import app.lumen.player.ui.LumenTheme
import app.lumen.player.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    private var engine by mutableStateOf<PlayerEngine?>(null)
    private var diskRecents by mutableStateOf<List<app.lumen.player.playback.RecentItem>>(emptyList())
    private var inPip by mutableStateOf(false)
    private var bound = false
    private var binding = false
    private var pending: ((PlayerEngine) -> Unit)? = null
    private var brightOrigin = -1f
    private var volumeOrigin = -1
    private val library by lazy { LibraryStore(this) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.LocalBinder
            val ready = binder.service.engine
            engine = ready
            bound = true
            binding = false
            pending?.invoke(ready)
            pending = null
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            engine = null
            bound = false
            binding = false
        }
    }

    private val openVideos = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) launchOpen(uris)
    }

    private val openSub = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            take(uri)
            engine?.addSubtitle(uri)
        }
    }

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        diskRecents = library.read()
        bindIfNeeded()
        setContent {
            LumenTheme {
                val current = engine
                if (current != null) {
                    PlayingOrHome(
                        engine = current,
                        inPip = inPip,
                        diskRecents = diskRecents,
                        showHint = libraryHints(),
                        onOpen = { openVideos.launch(arrayOf("*/*")) },
                        onRecent = { item ->
                            val resume = if (item.positionMs > 3_000 && (item.durationMs == 0L || item.positionMs < item.durationMs - 4_000)) {
                                item.positionMs
                            } else 0L
                            launchOpen(listOf(Uri.parse(item.uri)), resume)
                        },
                        onBack = { current.closeToLibrary() },
                        onPickSub = { openSub.launch(arrayOf("*/*")) },
                        onBrightness = ::gestureBrightness,
                        onVolume = ::gestureVolume,
                        onPip = { enterPip() },
                        onHintDone = { app.lumen.player.data.Prefs(this@MainActivity).hints = false },
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Ink)
                            .windowInsetsPadding(WindowInsets.safeDrawing),
                    ) {
                        HomeScreen(
                            recents = diskRecents,
                            onOpen = { openVideos.launch(arrayOf("*/*")) },
                            onRecent = { item ->
                                val resume = if (item.positionMs > 3_000 && (item.durationMs == 0L || item.positionMs < item.durationMs - 4_000)) {
                                    item.positionMs
                                } else 0L
                                launchOpen(listOf(Uri.parse(item.uri)), resume)
                            },
                        )
                    }
                }
            }
        }
        handleViewIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        diskRecents = library.read()
        bindIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = engine?.state?.value ?: return
        if (state.phase == Phase.Player && state.playing && state.pipOnLeave) {
            enterPip()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val player = engine
        if (player?.state?.value?.phase == Phase.Player) {
            when (keyCode) {
                KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    player.toggle()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    player.seekBy(10_000)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    player.seekBy(-10_000)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        val playing = engine?.state?.value?.phase == Phase.Player
        if (bound) {
            unbindService(connection)
            bound = false
        }
        if (!playing) {
            stopService(Intent(this, PlayerService::class.java))
        }
        super.onDestroy()
    }

    private fun libraryHints(): Boolean = app.lumen.player.data.Prefs(this).hints

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        launchOpen(listOf(uri), null)
    }

    private fun launchOpen(uris: List<Uri>, start: Long? = null) {
        uris.forEach(::take)
        val items = uris.map { QueueItem(it.toString(), displayName(it)) }
        ensureEngine { eng ->
            val startMs = when {
                start != null -> start
                items.size == 1 -> {
                    val recent = library.read().firstOrNull { it.uri == items[0].uri }
                    if (recent != null && recent.positionMs > 3_000 &&
                        (recent.durationMs == 0L || recent.positionMs < recent.durationMs - 4_000)
                    ) recent.positionMs else 0L
                }
                else -> 0L
            }
            eng.open(items, 0, startMs)
        }
    }

    private fun ensureEngine(block: (PlayerEngine) -> Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        val intent = Intent(this, PlayerService::class.java)
        startForegroundService(intent)
        val ready = engine
        if (ready != null) block(ready) else pending = block
        bindIfNeeded()
    }

    private fun bindIfNeeded() {
        if (bound || binding) return
        binding = true
        bindService(Intent(this, PlayerService::class.java), connection, BIND_AUTO_CREATE)
    }

    private fun take(uri: Uri) {
        if (uri.scheme != "content") return
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { null } ?: "Video"
    }

    private fun gestureBrightness(phase: Int, dy: Float, height: Float): Float {
        val attrs = window.attributes
        if (phase == 0 || brightOrigin < 0f) {
            brightOrigin = if (attrs.screenBrightness < 0f) 0.6f else attrs.screenBrightness
        }
        if (phase == 2) {
            val done = brightOrigin
            brightOrigin = -1f
            return done.coerceIn(0.02f, 1f)
        }
        val next = (brightOrigin - dy / height.coerceAtLeast(1f)).coerceIn(0.02f, 1f)
        attrs.screenBrightness = next
        window.attributes = attrs
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        return next
    }

    private fun gestureVolume(phase: Int, dy: Float, height: Float): Float {
        val audio = getSystemService(AudioManager::class.java)
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (phase == 0 || volumeOrigin < 0) {
            volumeOrigin = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        }
        if (phase == 2) {
            val done = volumeOrigin
            volumeOrigin = -1
            return done / max.toFloat()
        }
        val next = (volumeOrigin - dy / height.coerceAtLeast(1f) * max).toInt().coerceIn(0, max)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        return next / max.toFloat()
    }

    private fun enterPip() {
        val state = engine?.state?.value ?: return
        if (state.phase != Phase.Player) return
        var w = state.videoWidth.coerceAtLeast(16)
        var h = state.videoHeight.coerceAtLeast(9)
        val ratio = w.toFloat() / h
        if (ratio > 2.3f) w = (h * 2.3f).toInt()
        if (ratio < 0.42f) h = (w / 0.42f).toInt()
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(w, h))
            .build()
        runCatching { enterPictureInPictureMode(params) }
    }
}

@Composable
private fun PlayingOrHome(
    engine: PlayerEngine,
    inPip: Boolean,
    diskRecents: List<RecentItem>,
    showHint: Boolean,
    onOpen: () -> Unit,
    onRecent: (RecentItem) -> Unit,
    onBack: () -> Unit,
    onPickSub: () -> Unit,
    onBrightness: (Int, Float, Float) -> Float,
    onVolume: (Int, Float, Float) -> Float,
    onPip: () -> Unit,
    onHintDone: () -> Unit,
) {
    val ui by engine.state.collectAsState()
    val view = LocalView.current
    DisposableEffect(ui.playing, ui.phase) {
        view.keepScreenOn = ui.phase == Phase.Player && ui.playing
        onDispose { view.keepScreenOn = false }
    }
    if (ui.phase == Phase.Player) {
        PlayerScreen(
            state = ui,
            inPip = inPip,
            showHint = showHint,
            engine = engine,
            onBack = onBack,
            onPickSub = onPickSub,
            onBrightness = onBrightness,
            onVolume = onVolume,
            onPip = onPip,
            onHintDone = onHintDone,
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(Ink)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            HomeScreen(recents = ui.recents.ifEmpty { diskRecents }, onOpen = onOpen, onRecent = onRecent)
        }
    }
}
