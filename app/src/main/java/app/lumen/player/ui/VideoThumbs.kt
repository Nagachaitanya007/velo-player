package app.lumen.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

object VideoThumbs {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun peek(uri: String): ImageBitmap? = cache.get(uri)

    fun load(context: Context, uri: Uri): ImageBitmap? {
        val key = uri.toString()
        cache.get(key)?.let { return it }
        val bitmap = runCatching { stored(context, uri) }.getOrNull()
            ?: runCatching { frame(context, uri) }.getOrNull()
            ?: return null
        if (bitmap.width <= 0 || bitmap.height <= 0) return null
        val image = bitmap.asImageBitmap()
        cache.put(key, image)
        return image
    }

    private fun stored(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            return context.contentResolver.loadThumbnail(uri, Size(640, 360), null)
        }
        val id = android.content.ContentUris.parseId(uri)
        return MediaStore.Video.Thumbnails.getThumbnail(
            context.contentResolver,
            id,
            MediaStore.Video.Thumbnails.MINI_KIND,
            null,
        )
    }

    private fun frame(context: Context, uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            return if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 640, 360)
            } else {
                retriever.getFrameAtTime(1_500_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
        } finally {
            runCatching { retriever.release() }
        }
    }
}
