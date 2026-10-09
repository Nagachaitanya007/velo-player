package app.velo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.base.TrackDescription
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.io.File
import java.nio.ByteBuffer
import javax.swing.SwingUtilities

interface Kernel32 : com.sun.jna.Library {
    fun SetDllDirectoryA(path: String): Boolean
}

class BundledVlc(private val root: File) : BaseNativeDiscoveryStrategy(
    arrayOf("libvlc\\.dll", "libvlc\\.dylib", "libvlc\\.so(?:\\.\\d+)*"),
    arrayOf("%s/plugins", "%s\\plugins", "%s/../plugins"),
) {
    override fun supported() = true

    override fun discoveryDirectories(): List<String> {
        val lib = File(root, "lib")
        return if (lib.isDirectory) listOf(root.absolutePath, lib.absolutePath) else listOf(root.absolutePath)
    }

    override fun setPluginPath(pluginPath: String): Boolean {
        return if (RuntimeUtil.isWindows()) {
            LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=$pluginPath") == 0
        } else {
            LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", pluginPath, 1) == 0
        }
    }

    override fun onFound(path: String): Boolean {
        if (RuntimeUtil.isWindows()) {
            val kernel = Native.load("kernel32", Kernel32::class.java) as Kernel32
            kernel.SetDllDirectoryA(path)
        }
        if (RuntimeUtil.isMac()) {
            NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
            runCatching { NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName()) }
        }
        return super.onFound(path)
    }
}

fun locateVlc(): File? {
    val resources = System.getProperty("compose.application.resources.dir")
    val candidates = buildList {
        if (!resources.isNullOrBlank()) add(File(resources))
        add(File("vlc-bundle"))
        add(File("desktop/vlc-bundle"))
        if (RuntimeUtil.isWindows()) add(File("C:/Program Files/VideoLAN/VLC"))
        if (RuntimeUtil.isMac()) add(File("/Applications/VLC.app/Contents/MacOS"))
        if (RuntimeUtil.isNix()) {
            add(File("/usr/lib"))
            add(File("/usr/lib/x86_64-linux-gnu"))
        }
    }
    return candidates.firstOrNull { dir ->
        dir.isDirectory && dir.walkTopDown().any { it.name.startsWith("libvlc") && it.isFile }
    }
}

class Desk(
    val file: File,
    val positionMs: Long,
    val durationMs: Long,
    val openedAt: Long,
)

class Library {
    private val file = File(System.getProperty("user.home"), ".velo/recent.tsv")

    fun read(): List<Desk> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val bits = line.split('\t')
                if (bits.size < 5) return@mapNotNull null
                Desk(
                    file = File(unescape(bits[1])),
                    positionMs = bits[2].toLongOrNull() ?: 0L,
                    durationMs = bits[3].toLongOrNull() ?: 0L,
                    openedAt = bits[4].toLongOrNull() ?: 0L,
                )
            }.filter { it.file.exists() }
        }.getOrElse { emptyList() }
    }

    fun remember(item: Desk) {
        val next = listOf(item) + read().filter { it.file.absolutePath != item.file.absolutePath }
        write(next.take(40))
    }

    fun forget(path: String) {
        write(read().filter { it.file.absolutePath != path })
    }

    fun clear() = write(emptyList())

    private fun write(items: List<Desk>) {
        file.parentFile.mkdirs()
        file.writeText(
            items.joinToString("\n") {
                listOf(escape(it.file.name), escape(it.file.absolutePath), it.positionMs, it.durationMs, it.openedAt)
                    .joinToString("\t")
            },
        )
    }

    private fun escape(value: String) = value.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    private fun unescape(value: String) = value.replace("\\t", "\t").replace("\\n", "\n").replace("\\\\", "\\")
}

class Engine {
    val library = Library()
    var failure by mutableStateOf<String?>(null)
        private set
    var recents by mutableStateOf(library.read())
        private set
    var phase by mutableStateOf("home")
        private set
    var title by mutableStateOf("")
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var playing by mutableStateOf(false)
        private set
    var opening by mutableStateOf(false)
        private set
    var ended by mutableStateOf(false)
        private set
    var playError by mutableStateOf<String?>(null)
        private set
    var volume by mutableStateOf(100)
        private set
    var rate by mutableStateOf(1f)
        private set
    var brightness by mutableStateOf(1f)
        private set
    var contrast by mutableStateOf(1f)
        private set
    var saturation by mutableStateOf(1f)
        private set
    var gamma by mutableStateOf(1f)
        private set
    var fit by mutableStateOf("fit")
        private set
    var hw by mutableStateOf(true)
        private set
    var audioTracks by mutableStateOf<List<TrackDescription>>(emptyList())
        private set
    var spuTracks by mutableStateOf<List<TrackDescription>>(emptyList())
        private set
    var audioId by mutableStateOf(-1)
        private set
    var spuId by mutableStateOf(-1)
        private set
    var audioDelayMs by mutableStateOf(0L)
        private set
    var spuDelayMs by mutableStateOf(0L)
        private set
    var eqNames by mutableStateOf<List<String>>(emptyList())
        private set
    var eqName by mutableStateOf<String?>(null)
        private set
    var frame by mutableStateOf<ImageBitmap?>(null)
        private set
    var tick by mutableStateOf(0)
        private set

    private var factory: MediaPlayerFactory? = null
    private var player: uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer? = null
    private var current: File? = null
    private var pendingSeek = 0L
    private var lastFrameAt = 0L
    private var held: Image? = null
    private val prefs = File(System.getProperty("user.home"), ".velo/look.properties")

    fun start() {
        loadLook()
        val root = locateVlc()
        if (root == null) {
            failure = "Velo couldn't find its video engine."
            bump()
            return
        }
        try {
            val plugins = listOf(File(root, "plugins"), File(root, "lib").resolve("../plugins"))
                .map { it.canonicalFile }
                .firstOrNull { it.isDirectory }
            val args = mutableListOf("--no-video-title-show", "--audio-time-stretch")
            if (plugins != null) args += "--plugin-path=${plugins.absolutePath}"
            val created = MediaPlayerFactory(NativeDiscovery(BundledVlc(root)), *args.toTypedArray())
            factory = created
            val media = created.mediaPlayers().newEmbeddedMediaPlayer()
            player = media
            eqNames = runCatching { created.equalizer().presets() }.getOrDefault(emptyList())
            media.videoSurface().set(created.videoSurfaces().newVideoSurface(formats, painter, true))
            media.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun opening(mediaPlayer: MediaPlayer) = later { opening = true; playError = null; bump() }
                override fun playing(mediaPlayer: MediaPlayer) = later {
                    opening = false
                    playing = true
                    ended = false
                    playError = null
                    applyLook()
                    if (pendingSeek > 800) media.controls().setTime(pendingSeek)
                    pendingSeek = 0
                    refreshTracks()
                    bump()
                }

                override fun paused(mediaPlayer: MediaPlayer) = later {
                    playing = false
                    remember()
                    bump()
                }

                override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) = later {
                    positionMs = newTime
                    if (durationMs <= 0) durationMs = media.status().length().coerceAtLeast(0)
                    bump()
                }

                override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) = later {
                    durationMs = newLength
                    bump()
                }

                override fun finished(mediaPlayer: MediaPlayer) = later {
                    playing = false
                    ended = true
                    positionMs = durationMs
                    remember()
                    bump()
                }

                override fun error(mediaPlayer: MediaPlayer) = later {
                    opening = false
                    playing = false
                    playError = "This file didn't open."
                    bump()
                }
            })
            media.audio().setVolume(volume)
            if (!eqName.isNullOrBlank()) setEqualizer(eqName)
        } catch (_: Throwable) {
            failure = "Velo couldn't start the video engine."
        }
        bump()
    }

    fun open(file: File, startMs: Long) {
        val media = player ?: return
        current = file
        title = file.name
        phase = "player"
        opening = true
        ended = false
        playError = null
        positionMs = startMs
        durationMs = 0
        pendingSeek = startMs
        val options = mutableListOf<String>()
        if (!hw) options += ":avcodec-hw=none"
        media.media().play(file.absolutePath, *options.toTypedArray())
        bump()
    }

    fun retry() {
        val file = current ?: return
        open(file, 0)
    }

    fun toggle() {
        val media = player ?: return
        if (ended) {
            current?.let { open(it, 0) }
            return
        }
        if (media.status().isPlaying) media.controls().pause() else media.controls().play()
    }

    fun seekBy(ms: Long) {
        val next = (positionMs + ms).coerceAtLeast(0)
        player?.controls()?.setTime(next)
        positionMs = next
        bump()
    }

    fun seekTo(ms: Long) {
        val next = ms.coerceAtLeast(0)
        player?.controls()?.setTime(next)
        positionMs = next
        bump()
    }

    @JvmName("applyVolume")
    fun setVolume(value: Int) {
        volume = value.coerceIn(0, 150)
        player?.audio()?.setVolume(volume)
        saveLook()
        bump()
    }

    @JvmName("applyRate")
    fun setRate(value: Float) {
        rate = value.coerceIn(0.5f, 2f)
        player?.controls()?.setRate(rate)
        saveLook()
        bump()
    }

    fun setPicture(b: Float, c: Float, s: Float, g: Float) {
        brightness = b
        contrast = c
        saturation = s
        gamma = g
        applyLook()
        saveLook()
        bump()
    }

    @JvmName("applyFit")
    fun setFit(mode: String) {
        fit = mode
        saveLook()
        bump()
    }

    @JvmName("applyHw")
    fun setHw(enabled: Boolean) {
        hw = enabled
        saveLook()
        val file = current
        if (file != null && phase == "player") open(file, positionMs) else bump()
    }

    fun setAudio(id: Int) {
        player?.audio()?.setTrack(id)
        audioId = id
        bump()
    }

    fun setSpu(id: Int) {
        player?.subpictures()?.setTrack(id)
        spuId = id
        bump()
    }

    fun addSubtitle(file: File) {
        player?.subpictures()?.setSubTitleFile(file)
        later {
            refreshTracks()
            bump()
        }
    }

    fun setAudioDelay(ms: Long) {
        audioDelayMs = ms.coerceIn(-5_000, 5_000)
        player?.audio()?.setDelay(audioDelayMs * 1000)
        bump()
    }

    fun setSpuDelay(ms: Long) {
        spuDelayMs = ms.coerceIn(-5_000, 5_000)
        player?.subpictures()?.setDelay(spuDelayMs * 1000)
        bump()
    }

    fun setEqualizer(name: String?) {
        val media = player ?: return
        val created = factory ?: return
        if (name.isNullOrBlank()) {
            media.audio().setEqualizer(null)
            eqName = null
        } else {
            val eq = runCatching { created.equalizer().newEqualizer(name) }.getOrNull() ?: return
            media.audio().setEqualizer(eq)
            eqName = name
        }
        saveLook()
        bump()
    }

    fun resetLook() {
        brightness = 1f
        contrast = 1f
        saturation = 1f
        gamma = 1f
        rate = 1f
        volume = 100
        fit = "fit"
        eqName = null
        player?.controls()?.setRate(1f)
        player?.audio()?.setVolume(100)
        player?.audio()?.setEqualizer(null)
        applyLook()
        saveLook()
        bump()
    }

    fun back() {
        remember()
        runCatching { player?.controls()?.stop() }
        phase = "home"
        playing = false
        opening = false
        bump()
    }

    fun forget(path: String) {
        library.forget(path)
        recents = library.read()
        bump()
    }

    fun clearHistory() {
        library.clear()
        recents = emptyList()
        bump()
    }

    fun release() {
        remember()
        runCatching { player?.release() }
        runCatching { factory?.release() }
        runCatching { held?.close() }
    }

    private fun refreshTracks() {
        val media = player ?: return
        audioTracks = runCatching { media.audio().trackDescriptions() }.getOrDefault(emptyList())
        spuTracks = runCatching { media.subpictures().trackDescriptions() }.getOrDefault(emptyList())
        audioId = runCatching { media.audio().track() }.getOrDefault(-1)
        spuId = runCatching { media.subpictures().track() }.getOrDefault(-1)
    }

    private fun applyLook() {
        val media = player ?: return
        val plain = brightness == 1f && contrast == 1f && saturation == 1f && gamma == 1f
        media.video().setAdjustVideo(!plain)
        if (!plain) {
            media.video().setBrightness(brightness)
            media.video().setContrast(contrast)
            media.video().setSaturation(saturation)
            media.video().setGamma(gamma)
        }
        media.controls().setRate(rate)
        media.audio().setVolume(volume)
    }

    private fun remember() {
        val file = current ?: return
        if (phase != "player") return
        library.remember(Desk(file, positionMs.coerceAtLeast(0), durationMs.coerceAtLeast(0), System.currentTimeMillis()))
        recents = library.read()
    }

    private fun loadLook() {
        if (!prefs.exists()) return
        val map = prefs.readLines().mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
        brightness = map["brightness"]?.toFloatOrNull() ?: brightness
        contrast = map["contrast"]?.toFloatOrNull() ?: contrast
        saturation = map["saturation"]?.toFloatOrNull() ?: saturation
        gamma = map["gamma"]?.toFloatOrNull() ?: gamma
        volume = map["volume"]?.toIntOrNull() ?: volume
        rate = map["rate"]?.toFloatOrNull() ?: rate
        hw = map["hw"]?.toBooleanStrictOrNull() ?: hw
        eqName = map["eq"]?.ifBlank { null }
        fit = map["fit"] ?: fit
    }

    private fun saveLook() {
        prefs.parentFile.mkdirs()
        prefs.writeText(
            listOf(
                "brightness=$brightness",
                "contrast=$contrast",
                "saturation=$saturation",
                "gamma=$gamma",
                "volume=$volume",
                "rate=$rate",
                "hw=$hw",
                "eq=${eqName.orEmpty()}",
                "fit=$fit",
            ).joinToString("\n"),
        )
    }

    private val formats = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            return RV32BufferFormat(sourceWidth.coerceAtLeast(2), sourceHeight.coerceAtLeast(2))
        }

        override fun newFormatSize(bufferWidth: Int, bufferHeight: Int, displayWidth: Int, displayHeight: Int) = Unit
        override fun allocatedBuffers(buffers: Array<ByteBuffer>) = Unit
    }

    private val painter = object : RenderCallback {
        override fun lock(mediaPlayer: MediaPlayer) = Unit
        override fun unlock(mediaPlayer: MediaPlayer) = Unit
        override fun display(
            mediaPlayer: MediaPlayer,
            nativeBuffers: Array<ByteBuffer>,
            bufferFormat: BufferFormat,
            displayWidth: Int,
            displayHeight: Int,
        ) {
            val now = System.nanoTime()
            if (now - lastFrameAt < 33_000_000L) return
            lastFrameAt = now
            val w = bufferFormat.width
            val h = bufferFormat.height
            if (w <= 0 || h <= 0) return
            val pitch = bufferFormat.pitches.firstOrNull()?.coerceAtLeast(w * 4) ?: (w * 4)
            val src = nativeBuffers[0].duplicate()
            src.clear()
            val bytes = ByteArray(w * h * 4)
            var y = 0
            while (y < h) {
                val start = y * pitch
                if (start + w * 4 > src.limit()) break
                src.position(start)
                src.get(bytes, y * w * 4, w * 4)
                y++
            }
            var i = 3
            while (i < bytes.size) {
                bytes[i] = 0xFF.toByte()
                i += 4
            }
            SwingUtilities.invokeLater {
                val image = Image.makeRaster(
                    ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE),
                    bytes,
                    w * 4,
                )
                val previous = held
                held = image
                frame = image.toComposeImageBitmap()
                bump()
                SwingUtilities.invokeLater { previous?.close() }
            }
        }
    }

    private fun later(block: () -> Unit) {
        SwingUtilities.invokeLater(block)
    }

    private fun bump() {
        tick++
    }
}
