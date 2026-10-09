package app.lumen.player.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import app.lumen.player.data.LibraryStore
import app.lumen.player.data.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout

class PlayerEngine(context: Context) {
    private val app = context.applicationContext
    private val prefs = Prefs(app)
    private val library = LibraryStore(app)
    private val main = Handler(Looper.getMainLooper())
    private val lib = LibVLC(
        app,
        arrayListOf(
            "--audio-time-stretch",
            "--no-video-title-show",
            "--subsdec-encoding=UTF-8",
        ),
    )
    private val player = MediaPlayer(lib)
    private var attached = false
    private var pendingSeek = 0L
    private var expectStop = false
    private var subtitleUri: Uri? = null
    private var chosenAudio: Int? = null
    private var chosenSpu: Int? = null
    private var savedRate = 1f
    private var equalizer: MediaPlayer.Equalizer? = null
    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable { pause() }

    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    init {
        player.setEventListener { event ->
            main.post { onEvent(event) }
        }
    }

    private fun initialState(): PlayerUiState {
        val bands = runCatching { MediaPlayer.Equalizer.getBandCount() }.getOrDefault(0)
        val presets = runCatching { MediaPlayer.Equalizer.getPresetCount() }.getOrDefault(0)
        return PlayerUiState(
            brightness = prefs.brightness,
            contrast = prefs.contrast,
            saturation = prefs.saturation,
            gamma = prefs.gamma,
            rotation = prefs.rotation,
            deinterlace = prefs.deinterlace,
            scaleName = prefs.scaleName,
            subSize = prefs.subSize,
            subColor = prefs.subColor,
            hw = prefs.hw,
            pipOnLeave = prefs.pipOnLeave,
            eqPreset = prefs.eqPreset,
            eqEnabled = prefs.eqPreset >= 0,
            eqNames = (0 until presets).map { MediaPlayer.Equalizer.getPresetName(it) },
            eqFreqs = (0 until bands).map { MediaPlayer.Equalizer.getBandFrequency(it).toInt() },
            eqAmps = List(bands) { 0f },
            recents = library.read(),
        )
    }

    fun attach(layout: VLCVideoLayout) {
        if (attached) {
            runCatching { player.detachViews() }
        }
        player.attachViews(layout, null, false, true)
        attached = true
        applyScale()
    }

    fun detach() {
        if (!attached) return
        runCatching { player.detachViews() }
        attached = false
    }

    fun open(items: List<QueueItem>, index: Int, startMs: Long) {
        if (items.isEmpty()) return
        val safeIndex = index.coerceIn(0, items.lastIndex)
        chosenAudio = null
        chosenSpu = null
        subtitleUri = null
        expectStop = true
        runCatching { if (player.isPlaying || player.hasMedia()) player.stop() }
        _state.update {
            it.copy(
                phase = Phase.Player,
                queue = items,
                queueIndex = safeIndex,
                title = items[safeIndex].title,
                uri = items[safeIndex].uri,
                error = null,
                ended = false,
                opening = true,
                positionMs = startMs,
                audioDelayMs = 0,
                spuDelayMs = 0,
                abA = null,
                abB = null,
                hasVideo = true,
                chapters = emptyList(),
                videoInfo = "",
                resumeHintMs = if (startMs > 3_000) startMs else 0,
            )
        }
        pendingSeek = startMs
        playCurrent()
    }

    fun retry() {
        val s = _state.value
        if (s.queue.isEmpty()) return
        pendingSeek = s.positionMs
        expectStop = true
        runCatching { player.stop() }
        _state.update { it.copy(error = null, opening = true, ended = false) }
        playCurrent()
    }

    fun closeToLibrary() {
        rememberProgress()
        expectStop = true
        runCatching { player.stop() }
        clearSleep()
        _state.update {
            it.copy(
                phase = Phase.Library,
                playing = false,
                opening = false,
                boosting = false,
                uri = null,
            )
        }
    }

    fun toggle() {
        val s = _state.value
        if (s.ended) {
            pendingSeek = 0
            expectStop = true
            runCatching { player.stop() }
            _state.update { it.copy(ended = false, opening = true, positionMs = 0) }
            playCurrent()
            return
        }
        if (player.isPlaying) pause() else player.play()
    }

    fun pause() {
        if (player.isPlaying) player.pause()
    }

    fun play() {
        if (_state.value.ended) toggle() else if (!player.isPlaying) player.play()
    }

    fun seekTo(ms: Long) {
        val dur = _state.value.durationMs
        val target = if (dur > 0) ms.coerceIn(0, dur) else ms.coerceAtLeast(0)
        runCatching { player.setTime(target) }
        _state.update { it.copy(positionMs = target, ended = false, resumeHintMs = if (target < 1_000) 0 else it.resumeHintMs) }
    }

    fun seekBy(delta: Long) {
        seekTo(safeTime() + delta)
    }

    fun previous() {
        val s = _state.value
        if (safeTime() > 3_000) {
            seekTo(0)
            return
        }
        if (s.queueIndex > 0) open(s.queue, s.queueIndex - 1, 0)
    }

    fun next() {
        val s = _state.value
        if (s.queueIndex + 1 < s.queue.size) open(s.queue, s.queueIndex + 1, 0)
    }

    fun clearResumeHint() {
        _state.update { it.copy(resumeHintMs = 0) }
    }

    fun setRate(rate: Float) {
        val r = rate.coerceIn(0.25f, 4f)
        savedRate = r
        player.rate = r
        _state.update { it.copy(rate = r) }
    }

    fun setBoost(active: Boolean) {
        if (active) {
            if (!player.isPlaying) return
            savedRate = _state.value.rate
            player.rate = 2f
            _state.update { it.copy(boosting = true) }
        } else if (_state.value.boosting) {
            player.rate = savedRate
            _state.update { it.copy(boosting = false, rate = savedRate) }
        }
    }

    fun selectAudio(id: Int) {
        chosenAudio = id
        runCatching { player.setAudioTrack(id) }
        _state.update { it.copy(audioTrackId = id) }
    }

    fun selectSpu(id: Int) {
        chosenSpu = id
        runCatching { player.setSpuTrack(id) }
        _state.update { it.copy(spuTrackId = id) }
    }

    fun addSubtitle(uri: Uri) {
        subtitleUri = uri
        runCatching { player.addSlave(IMedia.Slave.Type.Subtitle, uri, true) }
    }

    fun setAudioDelay(ms: Long) {
        val v = ms.coerceIn(-10_000, 10_000)
        runCatching { player.setAudioDelay(v * 1000) }
        _state.update { it.copy(audioDelayMs = v) }
    }

    fun setSpuDelay(ms: Long) {
        val v = ms.coerceIn(-10_000, 10_000)
        runCatching { player.setSpuDelay(v * 1000) }
        _state.update { it.copy(spuDelayMs = v) }
    }

    fun setScale(name: String) {
        prefs.scaleName = name
        _state.update { it.copy(scaleName = name, zoom = 1f) }
        applyScale()
    }

    fun setZoom(zoom: Float) {
        val z = zoom.coerceIn(1f, 4f)
        _state.update { it.copy(zoom = z) }
        applyScale()
    }

    fun setRepeat(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    fun markA() {
        val t = safeTime()
        _state.update { s ->
            val b = s.abB?.takeIf { it > t }
            s.copy(abA = t, abB = b)
        }
    }

    fun markB() {
        val t = safeTime()
        _state.update { s ->
            if (s.abA != null && t > s.abA) s.copy(abB = t) else s
        }
    }

    fun clearAb() {
        _state.update { it.copy(abA = null, abB = null) }
    }

    fun setSleepMinutes(minutes: Int) {
        clearSleep()
        if (minutes <= 0) {
            _state.update { it.copy(sleepUntil = null, stopAtEnd = false) }
            return
        }
        val until = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepHandler.postDelayed(sleepRunnable, minutes * 60_000L)
        _state.update { it.copy(sleepUntil = until, stopAtEnd = false) }
    }

    fun setStopAtEnd(enabled: Boolean) {
        clearSleep()
        _state.update { it.copy(stopAtEnd = enabled, sleepUntil = null) }
    }

    fun setHw(enabled: Boolean) {
        prefs.hw = enabled
        _state.update { it.copy(hw = enabled) }
        if (_state.value.phase == Phase.Player) reload()
    }

    fun setPipOnLeave(enabled: Boolean) {
        prefs.pipOnLeave = enabled
        _state.update { it.copy(pipOnLeave = enabled) }
    }

    fun commitPicture(
        brightness: Float,
        contrast: Float,
        saturation: Float,
        gamma: Float,
    ) {
        prefs.brightness = brightness
        prefs.contrast = contrast
        prefs.saturation = saturation
        prefs.gamma = gamma
        _state.update {
            it.copy(
                brightness = brightness,
                contrast = contrast,
                saturation = saturation,
                gamma = gamma,
            )
        }
        if (_state.value.phase == Phase.Player && _state.value.uri != null) reload()
    }

    fun resetPicture() {
        commitPicture(1f, 1f, 1f, 1f)
    }

    fun setRotation(degrees: Int) {
        val d = when (degrees) {
            90, 180, 270 -> degrees
            else -> 0
        }
        prefs.rotation = d
        _state.update { it.copy(rotation = d) }
        if (_state.value.uri != null && _state.value.phase == Phase.Player) reload()
    }

    fun setDeinterlace(mode: String) {
        prefs.deinterlace = mode
        _state.update { it.copy(deinterlace = mode) }
        if (_state.value.uri != null && _state.value.phase == Phase.Player) reload()
    }

    fun setSubtitleStyle(size: Int, color: Int) {
        val s = size.coerceIn(10, 64)
        prefs.subSize = s
        prefs.subColor = color
        _state.update { it.copy(subSize = s, subColor = color) }
        if (_state.value.uri != null && _state.value.phase == Phase.Player) reload()
    }

    fun setEqOff() {
        equalizer = null
        prefs.eqPreset = -1
        runCatching { player.setEqualizer(null) }
        _state.update { it.copy(eqEnabled = false, eqPreset = -1, eqPreamp = 0f, eqAmps = it.eqAmps.map { 0f }) }
    }

    fun setEqPreset(index: Int) {
        val eq = MediaPlayer.Equalizer.createFromPreset(index) ?: return
        equalizer = eq
        prefs.eqPreset = index
        player.setEqualizer(eq)
        _state.update {
            it.copy(
                eqEnabled = true,
                eqPreset = index,
                eqPreamp = eq.preAmp,
                eqAmps = ampsOf(eq, it.eqFreqs.size),
            )
        }
    }

    fun setCustomEq(amps: List<Float>, preamp: Float) {
        val eq = equalizer ?: MediaPlayer.Equalizer.create()
        equalizer = eq
        eq.setPreAmp(preamp)
        amps.forEachIndexed { i, amp -> eq.setAmp(i, amp) }
        prefs.eqPreset = -2
        player.setEqualizer(eq)
        _state.update {
            it.copy(eqEnabled = true, eqPreset = -2, eqPreamp = preamp, eqAmps = amps)
        }
    }

    fun release() {
        clearSleep()
        rememberProgress()
        detach()
        runCatching { player.release() }
        runCatching { lib.release() }
    }

    private fun playCurrent() {
        val s = _state.value
        val item = s.queue.getOrNull(s.queueIndex) ?: return
        val uri = Uri.parse(item.uri)
        val media = Media(lib, uri)
        media.setHWDecoderEnabled(s.hw, false)
        media.addOption(":audio-time-stretch")
        decorate(media, s)
        player.media = media
        media.release()
        player.play()
        _state.update {
            it.copy(title = item.title, uri = item.uri, opening = true, error = null)
        }
    }

    private fun reload() {
        val t = safeTime()
        pendingSeek = t
        expectStop = true
        chosenAudio = _state.value.audioTrackId.takeIf { it >= 0 }
        chosenSpu = _state.value.spuTrackId
        runCatching { player.stop() }
        playCurrent()
    }

    private fun decorate(media: Media, s: PlayerUiState) {
        val filters = mutableListOf<String>()
        val pictureChanged = s.brightness != 1f || s.contrast != 1f || s.saturation != 1f || s.gamma != 1f
        if (pictureChanged) {
            filters += "adjust"
            media.addOption(":brightness=${s.brightness}")
            media.addOption(":contrast=${s.contrast}")
            media.addOption(":saturation=${s.saturation}")
            media.addOption(":gamma=${s.gamma}")
        }
        if (s.rotation != 0) {
            filters += "transform"
            media.addOption(":transform-type=${s.rotation}")
        }
        if (filters.isNotEmpty()) {
            media.addOption(":video-filter=${filters.joinToString(",")}")
        }
        if (s.deinterlace != "off") {
            media.addOption(":deinterlace=1")
            media.addOption(":deinterlace-mode=${s.deinterlace}")
        }
        media.addOption(":freetype-rel-fontsize=${s.subSize}")
        media.addOption(":freetype-color=${s.subColor}")
        media.addOption(":freetype-outline-thickness=3")
        media.addOption(":freetype-outline-color=0")
    }

    private fun onEvent(event: MediaPlayer.Event) {
        when (event.type) {
            MediaPlayer.Event.Opening -> _state.update { it.copy(opening = true, error = null) }
            MediaPlayer.Event.Buffering -> _state.update { it.copy(buffering = event.buffering) }
            MediaPlayer.Event.Playing -> {
                expectStop = false
                applyPendingSeek()
                restoreRuntime()
                refreshTracks()
                refreshVideo()
                _state.update { it.copy(playing = true, opening = false, ended = false, error = null) }
                rememberProgress()
            }
            MediaPlayer.Event.Paused -> {
                _state.update { it.copy(playing = false, opening = false) }
                rememberProgress()
            }
            MediaPlayer.Event.Stopped -> expectStop = false
            MediaPlayer.Event.EndReached -> if (!expectStop) onEnded()
            MediaPlayer.Event.EncounteredError -> {
                expectStop = false
                _state.update {
                    it.copy(
                        opening = false,
                        playing = false,
                        error = "This file didn't open. The phone may not be able to read it.",
                    )
                }
            }
            MediaPlayer.Event.TimeChanged -> {
                val t = event.timeChanged
                val a = _state.value.abA
                val b = _state.value.abB
                if (a != null && b != null && b > a && t >= b) {
                    player.setTime(a)
                }
                _state.update { it.copy(positionMs = t, durationMs = player.length.coerceAtLeast(it.durationMs)) }
            }
            MediaPlayer.Event.LengthChanged -> {
                _state.update { it.copy(durationMs = event.lengthChanged) }
                rememberProgress()
            }
            MediaPlayer.Event.SeekableChanged -> {
                if (event.seekable) applyPendingSeek()
            }
            MediaPlayer.Event.Vout, MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESSelected -> {
                refreshTracks()
                refreshVideo()
                if (event.type == MediaPlayer.Event.Vout) {
                    _state.update { it.copy(hasVideo = event.voutCount > 0) }
                }
            }
        }
    }

    private fun onEnded() {
        val s = _state.value
        expectStop = true
        runCatching { player.stop() }
        when {
            s.stopAtEnd -> {
                _state.update { it.copy(playing = false, ended = true, opening = false, positionMs = it.durationMs) }
            }
            s.repeatMode == RepeatMode.One -> {
                pendingSeek = 0
                _state.update { it.copy(ended = false, positionMs = 0, opening = true) }
                playCurrent()
            }
            s.queueIndex + 1 < s.queue.size -> open(s.queue, s.queueIndex + 1, 0)
            s.repeatMode == RepeatMode.All && s.queue.isNotEmpty() -> open(s.queue, 0, 0)
            else -> _state.update {
                it.copy(playing = false, ended = true, opening = false, positionMs = it.durationMs)
            }
        }
    }

    private fun restoreRuntime() {
        val s = _state.value
        runCatching { player.rate = if (s.boosting) 2f else s.rate }
        runCatching { player.setAudioDelay(s.audioDelayMs * 1000) }
        runCatching { player.setSpuDelay(s.spuDelayMs * 1000) }
        applyScale()
        equalizer?.let { runCatching { player.setEqualizer(it) } }
        chosenAudio?.let { runCatching { player.setAudioTrack(it) } }
        chosenSpu?.let { runCatching { player.setSpuTrack(it) } }
        subtitleUri?.let { runCatching { player.addSlave(IMedia.Slave.Type.Subtitle, it, true) } }
    }

    private fun applyScale() {
        val s = _state.value
        val type = when (s.scaleName) {
            "crop" -> MediaPlayer.ScaleType.SURFACE_FIT_SCREEN
            "stretch" -> MediaPlayer.ScaleType.SURFACE_FILL
            "16:9" -> MediaPlayer.ScaleType.SURFACE_16_9
            "4:3" -> MediaPlayer.ScaleType.SURFACE_4_3
            "2.39" -> MediaPlayer.ScaleType.SURFACE_239_1
            "original" -> MediaPlayer.ScaleType.SURFACE_ORIGINAL
            else -> MediaPlayer.ScaleType.SURFACE_BEST_FIT
        }
        runCatching {
            player.videoScale = type
            player.scale = if (s.zoom <= 1.01f) 0f else s.zoom
            if (attached) player.updateVideoSurfaces()
        }
    }

    private fun refreshTracks() {
        val audio = runCatching { player.audioTracks?.map { TrackChoice(it.id, it.name) } }.getOrNull().orEmpty()
        val spu = runCatching { player.spuTracks?.map { TrackChoice(it.id, it.name) } }.getOrNull().orEmpty()
        val audioId = runCatching { player.audioTrack }.getOrDefault(-1)
        val spuId = runCatching { player.spuTrack }.getOrDefault(-1)
        _state.update {
            it.copy(
                audioTracks = audio,
                spuTracks = spu,
                audioTrackId = audioId,
                spuTrackId = spuId,
            )
        }
    }

    private fun refreshVideo() {
        val track = runCatching { player.currentVideoTrack }.getOrNull()
        if (track == null) return
        val fps = if (track.frameRateDen != 0) track.frameRateNum.toFloat() / track.frameRateDen else 0f
        val codec = track.codec?.takeIf { it.isNotBlank() }?.uppercase() ?: ""
        val fpsLabel = if (fps > 1f) " · ${"%.3g".format(fps)} fps" else ""
        val info = buildString {
            if (track.width > 0) append("${track.width}×${track.height}")
            if (codec.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append(codec)
            }
            append(fpsLabel)
        }
        val chapters = runCatching {
            val title = player.title.coerceAtLeast(0)
            player.getChapters(title)?.map {
                ChapterMark(it.name?.ifBlank { "Chapter" } ?: "Chapter", it.timeOffset)
            }
        }.getOrNull().orEmpty()
        _state.update {
            it.copy(
                videoInfo = info,
                videoWidth = track.width,
                videoHeight = track.height,
                hasVideo = track.width > 0,
                chapters = chapters,
            )
        }
    }

    private fun applyPendingSeek() {
        val target = pendingSeek
        if (target <= 500) {
            pendingSeek = 0
            return
        }
        if (runCatching { player.isSeekable }.getOrDefault(false)) {
            player.setTime(target)
            pendingSeek = 0
            _state.update { it.copy(positionMs = target) }
        }
    }

    private fun rememberProgress() {
        val s = _state.value
        val uri = s.uri ?: return
        if (s.phase != Phase.Player && s.title.isBlank()) return
        val item = RecentItem(
            uri = uri,
            title = s.title.ifBlank { "Video" },
            positionMs = s.positionMs.coerceAtLeast(0),
            durationMs = s.durationMs.coerceAtLeast(0),
            openedAt = System.currentTimeMillis(),
        )
        val recents = library.remember(item)
        _state.update { it.copy(recents = recents) }
    }

    private fun safeTime(): Long = runCatching { player.time }.getOrDefault(_state.value.positionMs)

    private fun clearSleep() {
        sleepHandler.removeCallbacks(sleepRunnable)
    }

    private fun ampsOf(eq: MediaPlayer.Equalizer, count: Int): List<Float> {
        return (0 until count).map { eq.getAmp(it) }
    }
}
