package app.velo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import kotlin.concurrent.thread

object Posters {
    private class Held(val bitmap: ImageBitmap, val raw: Image)

    private val memory = object : LinkedHashMap<String, Held>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Held>?): Boolean {
            if (size <= 80) return false
            runCatching { eldest?.value?.raw?.close() }
            return true
        }
    }
    private val waiters = HashMap<String, MutableList<(ImageBitmap) -> Unit>>()
    private val queue = LinkedBlockingQueue<File>()
    private val queued = HashSet<String>()
    private val failed = HashSet<String>()
    private val arrived = Semaphore(0)
    private val generation = AtomicInteger(0)
    private val seen = AtomicInteger(0)
    @Volatile private var accept = 0
    private val frame = AtomicReference<BufferedImage?>(null)
    private val dir = File(System.getProperty("user.home"), ".velo/thumbs")
    private var factory: MediaPlayerFactory? = null
    private var player: EmbeddedMediaPlayer? = null
    private var running = true
    private var started = false

    fun bind(factory: MediaPlayerFactory) {
        this.factory = factory
        synchronized(this) {
            if (started) return
            started = true
            thread(name = "velo-posters", isDaemon = true) { loop() }
        }
    }

    fun shutdown() {
        running = false
        arrived.release()
        queue.clear()
        val current = player
        player = null
        runCatching { current?.controls()?.stop() }
        runCatching { current?.release() }
    }

    fun peek(file: File): ImageBitmap? = synchronized(memory) { memory[key(file)]?.bitmap }

    fun want(file: File, onReady: (ImageBitmap) -> Unit) {
        if (!file.exists()) return
        peek(file)?.let { onReady(it); return }
        val id = key(file)
        val cached = File(dir, "$id.jpg")
        if (cached.exists() && cached.length() > 32) {
            thread(name = "velo-poster-read", isDaemon = true) {
                val held = decode(cached) ?: return@thread
                deliver(id, held, listOf(onReady))
            }
            return
        }
        synchronized(this) {
            if (failed.contains(id)) return
            waiters.getOrPut(id) { mutableListOf() }.add(onReady)
            if (queued.add(id)) queue.offer(file)
        }
    }

    private fun loop() {
        while (running) {
            val file = try {
                queue.take()
            } catch (_: InterruptedException) {
                continue
            }
            if (!running) return
            val id = key(file)
            val image = runCatching { grab(file) }.getOrNull()
            if (image != null) {
                val jpeg = File(dir, "$id.jpg")
                runCatching {
                    dir.mkdirs()
                    ImageIO.write(image, "jpg", jpeg)
                }
                val held = decode(jpeg) ?: encode(image)
                val pending = synchronized(this) {
                    queued.remove(id)
                    waiters.remove(id).orEmpty()
                }
                if (held != null) deliver(id, held, pending)
            } else {
                synchronized(this) {
                    queued.remove(id)
                    failed.add(id)
                    waiters.remove(id)
                }
            }
        }
    }

    private fun deliver(id: String, held: Held, listeners: List<(ImageBitmap) -> Unit>) {
        synchronized(memory) { memory[id] = held }
        listeners.forEach { listener -> runCatching { listener(held.bitmap) } }
    }

    private fun grab(file: File): BufferedImage? {
        val media = thumbPlayer() ?: return null
        val first = take(media, file, "start-time=2")
        if (first != null) return first
        return take(media, file, "start-time=0")
    }

    private fun take(media: EmbeddedMediaPlayer, file: File, option: String): BufferedImage? {
        val gen = generation.incrementAndGet()
        accept = gen
        seen.set(0)
        frame.set(null)
        arrived.drainPermits()
        val startedOk = runCatching {
            media.media().play(file.absolutePath, option, ":no-audio")
        }.isSuccess
        if (!startedOk) {
            accept = 0
            return null
        }
        val got = arrived.tryAcquire(6, java.util.concurrent.TimeUnit.SECONDS)
        val image = frame.getAndSet(null)
        accept = 0
        runCatching { media.controls().stop() }
        return if (got && image != null && generation.get() == gen) image else null
    }

    private fun thumbPlayer(): EmbeddedMediaPlayer? {
        player?.let { return it }
        val created = factory ?: return null
        return runCatching {
            val media = created.mediaPlayers().newEmbeddedMediaPlayer()
            media.videoSurface().set(created.videoSurfaces().newVideoSurface(formats, painter, true))
            media.audio().setVolume(0)
            media.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
                override fun error(mediaPlayer: MediaPlayer) {
                    arrived.release()
                }
            })
            player = media
            media
        }.getOrNull()
    }

    private val formats = object : BufferFormatCallback {
        override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
            val sw = sourceWidth.coerceAtLeast(2)
            val sh = sourceHeight.coerceAtLeast(2)
            val tw = if (sw < 480) sw - sw % 2 else 480
            var th = ((sh.toLong() * tw) / sw).toInt().coerceAtLeast(2)
            if (th % 2 != 0) th += 1
            return RV32BufferFormat(tw.coerceAtLeast(2), th.coerceAtLeast(2))
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
            if (accept == 0 || accept != generation.get()) return
            if (frame.get() != null) return
            val w = bufferFormat.width
            val h = bufferFormat.height
            if (w <= 1 || h <= 1 || w > 1920 || h > 1920) return
            if (seen.incrementAndGet() < 2) return
            val pitch = bufferFormat.pitches.firstOrNull()?.coerceAtLeast(w * 4) ?: (w * 4)
            val src = nativeBuffers.getOrNull(0)?.duplicate() ?: return
            src.clear()
            val bytes = ByteArray(w * h * 4)
            var y = 0
            while (y < h) {
                val start = y * pitch
                if (start + w * 4 > src.limit()) return
                src.position(start)
                src.get(bytes, y * w * 4, w * 4)
                y++
            }
            val pixels = IntArray(w * h)
            var i = 0
            var p = 0
            while (p < pixels.size) {
                val b = bytes[i].toInt() and 0xff
                val g = bytes[i + 1].toInt() and 0xff
                val r = bytes[i + 2].toInt() and 0xff
                pixels[p] = (r shl 16) or (g shl 8) or b
                i += 4
                p++
            }
            if (accept == 0 || accept != generation.get()) return
            val image = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
            image.setRGB(0, 0, w, h, pixels, 0, w)
            if (frame.compareAndSet(null, image)) arrived.release()
        }
    }

    private fun key(file: File): String {
        val raw = "${file.absolutePath}|${file.length()}|${file.lastModified()}"
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun decode(file: File): Held? {
        val bytes = runCatching { file.readBytes() }.getOrNull() ?: return null
        if (bytes.size < 32) return null
        val raw = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
        val bitmap = runCatching { raw.toComposeImageBitmap() }.getOrElse {
            raw.close()
            return null
        }
        return Held(bitmap, raw)
    }

    private fun encode(image: BufferedImage): Held? {
        val out = ByteArrayOutputStream()
        if (!ImageIO.write(image, "jpg", out)) return null
        val raw = runCatching { Image.makeFromEncoded(out.toByteArray()) }.getOrNull() ?: return null
        val bitmap = runCatching { raw.toComposeImageBitmap() }.getOrElse {
            raw.close()
            return null
        }
        return Held(bitmap, raw)
    }
}
