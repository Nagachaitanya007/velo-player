package app.lumen.player.data

import android.content.Context
import app.lumen.player.playback.RecentItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("lumen", Context.MODE_PRIVATE)

    var hw: Boolean
        get() = sp.getBoolean("hw", true)
        set(v) { sp.edit().putBoolean("hw", v).apply() }

    var brightness: Float
        get() = sp.getFloat("brightness", 1f)
        set(v) { sp.edit().putFloat("brightness", v).apply() }

    var contrast: Float
        get() = sp.getFloat("contrast", 1f)
        set(v) { sp.edit().putFloat("contrast", v).apply() }

    var saturation: Float
        get() = sp.getFloat("saturation", 1f)
        set(v) { sp.edit().putFloat("saturation", v).apply() }

    var gamma: Float
        get() = sp.getFloat("gamma", 1f)
        set(v) { sp.edit().putFloat("gamma", v).apply() }

    var rotation: Int
        get() = sp.getInt("rotation", 0)
        set(v) { sp.edit().putInt("rotation", v).apply() }

    var deinterlace: String
        get() = sp.getString("deinterlace", "off") ?: "off"
        set(v) { sp.edit().putString("deinterlace", v).apply() }

    var scaleName: String
        get() = sp.getString("scale", "fit") ?: "fit"
        set(v) { sp.edit().putString("scale", v).apply() }

    var subSize: Int
        get() = sp.getInt("subSize", 20)
        set(v) { sp.edit().putInt("subSize", v).apply() }

    var subColor: Int
        get() = sp.getInt("subColor", 0xFFFFFF)
        set(v) { sp.edit().putInt("subColor", v).apply() }

    var eqPreset: Int
        get() = sp.getInt("eqPreset", -1)
        set(v) { sp.edit().putInt("eqPreset", v).apply() }

    var pipOnLeave: Boolean
        get() = sp.getBoolean("pip", true)
        set(v) { sp.edit().putBoolean("pip", v).apply() }

    var hints: Boolean
        get() = sp.getBoolean("hints", true)
        set(v) { sp.edit().putBoolean("hints", v).apply() }

    var askedVideos: Boolean
        get() = sp.getBoolean("askedVideos", false)
        set(v) { sp.edit().putBoolean("askedVideos", v).apply() }
}

class LibraryStore(context: Context) {
    private val file = File(context.filesDir, "recent.json")
    private val lock = Any()

    fun read(): List<RecentItem> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        RecentItem(
                            uri = o.getString("uri"),
                            title = o.optString("title", "Video"),
                            positionMs = o.optLong("position"),
                            durationMs = o.optLong("duration"),
                            openedAt = o.optLong("opened"),
                        )
                    )
                }
            }
        }.getOrElse { emptyList() }
    }

    fun remember(item: RecentItem): List<RecentItem> = synchronized(lock) {
        val next = listOf(item) + read().filter { it.uri != item.uri }
        val trimmed = next.take(40)
        write(trimmed)
        trimmed
    }

    fun forget(uri: String): List<RecentItem> = synchronized(lock) {
        val next = read().filter { it.uri != uri }
        write(next)
        next
    }

    fun clear(): List<RecentItem> = synchronized(lock) {
        write(emptyList())
        emptyList()
    }

    private fun write(items: List<RecentItem>) {
        val arr = JSONArray()
        items.forEach { recent ->
            arr.put(
                JSONObject()
                    .put("uri", recent.uri)
                    .put("title", recent.title)
                    .put("position", recent.positionMs)
                    .put("duration", recent.durationMs)
                    .put("opened", recent.openedAt)
            )
        }
        file.writeText(arr.toString())
    }
}
