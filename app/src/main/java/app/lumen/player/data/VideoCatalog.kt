package app.lumen.player.data

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

data class PhoneVideo(
    val uri: String,
    val title: String,
    val folder: String,
    val path: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val addedMs: Long,
)

object VideoCatalog {
    private val extensions = arrayOf(
        "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "f4v",
        "3gp", "3g2", "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "vob",
        "asf", "divx", "rmvb", "rm", "qt", "tod",
    )

    fun scan(context: Context): List<PhoneVideo> {
        val found = LinkedHashMap<String, PhoneVideo>()
        for (volume in volumes(context)) {
            read(context, videoCollection(volume), pendingOnly(), null, found)
            read(context, filesCollection(volume), filesSelection(), filesArgs(), found)
        }
        return found.values.sortedByDescending { it.addedMs }
    }

    private fun volumes(context: Context): List<String> {
        if (Build.VERSION.SDK_INT < 29) return listOf("external")
        return runCatching { MediaStore.getExternalVolumeNames(context).toList() }
            .getOrDefault(listOf(MediaStore.VOLUME_EXTERNAL))
            .filter { it.isNotBlank() && it != MediaStore.VOLUME_INTERNAL }
            .ifEmpty { listOf(MediaStore.VOLUME_EXTERNAL) }
    }

    private fun videoCollection(volume: String): Uri {
        return if (Build.VERSION.SDK_INT >= 29) MediaStore.Video.Media.getContentUri(volume)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    private fun filesCollection(volume: String): Uri {
        return if (Build.VERSION.SDK_INT >= 29) MediaStore.Files.getContentUri(volume)
        else MediaStore.Files.getContentUri("external")
    }

    private fun pendingOnly(): String? {
        return if (Build.VERSION.SDK_INT >= 29) "${MediaStore.MediaColumns.IS_PENDING}=0" else null
    }

    private fun filesSelection(): String {
        val names = extensions.joinToString(" OR ") { "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?" }
        val body = "(${MediaStore.MediaColumns.MIME_TYPE} LIKE 'video/%' OR $names)"
        return if (Build.VERSION.SDK_INT >= 29) "${MediaStore.MediaColumns.IS_PENDING}=0 AND $body" else body
    }

    private fun filesArgs() = Array(extensions.size) { "%.${extensions[it]}" }

    private fun read(
        context: Context,
        collection: Uri,
        selection: String?,
        args: Array<String>?,
        into: LinkedHashMap<String, PhoneVideo>,
    ) {
        val resolver = context.contentResolver
        val full = if (Build.VERSION.SDK_INT >= 29) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DURATION,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.Video.Media.DURATION,
                MediaStore.MediaColumns.DATA,
            )
        }
        val cursor = query(resolver, collection, full, selection, args)
            ?: query(
                resolver,
                collection,
                arrayOf(
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.DATE_ADDED,
                ),
                selection,
                args,
            )
            ?: return
        cursor.use { rows ->
            val idCol = rows.getColumnIndex(MediaStore.MediaColumns._ID)
            val nameCol = rows.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            if (idCol < 0 || nameCol < 0) return
            val sizeCol = rows.getColumnIndex(MediaStore.MediaColumns.SIZE)
            val addedCol = rows.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
            val durCol = if (Build.VERSION.SDK_INT >= 29) {
                rows.getColumnIndex(MediaStore.MediaColumns.DURATION)
            } else {
                rows.getColumnIndex(MediaStore.Video.Media.DURATION)
            }
            val bucketCol = if (Build.VERSION.SDK_INT >= 29) rows.getColumnIndex(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME) else -1
            val relCol = if (Build.VERSION.SDK_INT >= 29) rows.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH) else -1
            val dataCol = rows.getColumnIndex(MediaStore.MediaColumns.DATA)
            while (rows.moveToNext()) {
                val name = rows.getString(nameCol)?.trim().orEmpty()
                if (name.isEmpty() || name.startsWith(".")) continue
                val id = rows.getLong(idCol)
                val size = if (sizeCol >= 0) rows.getLong(sizeCol) else 0L
                val added = if (addedCol >= 0) rows.getLong(addedCol) * 1000L else 0L
                val duration = if (durCol >= 0) rows.getLong(durCol) else 0L
                val folder = folder(rows, bucketCol, relCol, dataCol)
                val video = PhoneVideo(
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    title = name,
                    folder = folder,
                    path = place(rows, relCol, dataCol, folder),
                    durationMs = duration,
                    sizeBytes = size,
                    addedMs = added,
                )
                val key = "${folder.lowercase()}|${name.lowercase()}"
                val existing = into[key]
                if (existing == null || (existing.durationMs == 0L && duration > 0L)) into[key] = video
            }
        }
    }

    private fun query(
        resolver: android.content.ContentResolver,
        collection: Uri,
        projection: Array<String>,
        selection: String?,
        args: Array<String>?,
    ): Cursor? {
        return runCatching {
            resolver.query(collection, projection, selection, args, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
        }.getOrNull()
    }

    private fun folder(rows: Cursor, bucketCol: Int, relCol: Int, dataCol: Int): String {
        if (bucketCol >= 0) {
            val bucket = rows.getString(bucketCol)?.trim().orEmpty()
            if (bucket.isNotEmpty()) return bucket
        }
        if (relCol >= 0) {
            val rel = rows.getString(relCol)?.trim('/')?.substringAfterLast('/').orEmpty()
            if (rel.isNotEmpty()) return rel
        }
        if (dataCol >= 0) {
            val data = rows.getString(dataCol)
            if (!data.isNullOrBlank()) return File(data).parentFile?.name ?: "Phone"
        }
        return "Phone"
    }

    private fun place(rows: Cursor, relCol: Int, dataCol: Int, folder: String): String {
        if (relCol >= 0) {
            val rel = rows.getString(relCol)?.trim().orEmpty().trim('/')
            if (rel.isNotEmpty()) return rel
        }
        if (dataCol >= 0) {
            val data = rows.getString(dataCol)
            if (!data.isNullOrBlank()) {
                val parent = File(data).parent?.replace('\\', '/')
                if (!parent.isNullOrBlank()) {
                    val emulated = "/emulated/0/"
                    val at = parent.indexOf(emulated)
                    if (at >= 0) return parent.substring(at + emulated.length).trim('/')
                    val storage = Regex("^/storage/[^/]+/(.+)$").find(parent)
                    if (storage != null) return storage.groupValues[1].trim('/')
                }
            }
        }
        return folder
    }
}
