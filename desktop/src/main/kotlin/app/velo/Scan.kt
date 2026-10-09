package app.velo

import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread

data class DiskVideo(
    val file: File,
    val size: Long,
    val modified: Long,
)

private val videoExt = setOf(
    "mp4", "m4v", "mkv", "webm", "avi", "mov", "wmv", "flv", "f4v",
    "3gp", "3g2", "ts", "m2ts", "mts", "mpg", "mpeg", "ogv", "vob",
    "asf", "divx", "rmvb", "rm", "qt",
)

private val skipDir = setOf(
    "node_modules", ".git", ".gradle", "library", "appdata", "windows",
    "program files", "program files (x86)", "programdata", "\$recycle.bin",
    "system volume information", "recovery", ".trash", ".trashes",
    "applications", "caches", "cache", ".npm", ".cache", ".android",
    "sdk", "target", ".venv", "venv", "__pycache__", ".idea", ".vscode",
    "site-packages", ".cocoapods", "pods", "deriveddata", ".svn", ".hg",
    "proc", "sys", "dev", "lost+found", "application data", "windowsapps",
    "wpsystem", ".npm", "gradle", "caches",
)

fun scanDisk(cancelled: () -> Boolean, onUpdate: (List<DiskVideo>, Boolean) -> Unit) {
    val found = ArrayList<DiskVideo>(128)
    val seen = HashSet<String>()
    var announced = 0
    fun consider(file: File) {
        if (!file.isFile) return
        if (file.extension.lowercase() !in videoExt) return
        if (file.length() <= 0L) return
        val path = runCatching { file.absolutePath }.getOrNull() ?: return
        if (!seen.add(path)) return
        found += DiskVideo(file, file.length(), file.lastModified())
        if (found.size - announced >= 40) {
            announced = found.size
            onUpdate(found.toList(), true)
        }
    }
    fun walk(dir: File, depth: Int) {
        if (cancelled() || depth > 9 || found.size >= 4000) return
        val name = dir.name.lowercase()
        if (name.startsWith(".") || name in skipDir) return
        if (name.endsWith(".app") || name.endsWith(".photoslibrary") || name.endsWith(".bundle")) return
        if (runCatching { Files.isSymbolicLink(dir.toPath()) }.getOrDefault(true)) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            if (cancelled() || found.size >= 4000) return
            if (child.isDirectory) walk(child, depth + 1) else consider(child)
        }
    }
    for (root in scanRoots()) {
        if (cancelled() || found.size >= 4000) break
        if (root.isDirectory) walk(root, 0)
    }
    found.sortByDescending { it.modified }
    onUpdate(found, false)
}

fun startScan(cancelled: () -> Boolean, onUpdate: (List<DiskVideo>, Boolean) -> Unit) {
    thread(name = "velo-scan", isDaemon = true) {
        runCatching { scanDisk(cancelled, onUpdate) }
            .onFailure { onUpdate(emptyList(), false) }
    }
}

private fun scanRoots(): List<File> {
    val home = File(System.getProperty("user.home"))
    val roots = LinkedHashSet<File>()
    if (home.isDirectory) roots += home
    val os = System.getProperty("os.name").lowercase()
    when {
        os.contains("mac") -> {
            val skip = runCatching {
                setOf(File("/").canonicalPath, File("/System/Volumes/Data").canonicalPath, home.canonicalPath)
            }.getOrDefault(emptySet())
            File("/Volumes").listFiles()?.forEach { volume ->
                if (!volume.isDirectory) return@forEach
                val canon = runCatching { volume.canonicalPath }.getOrNull() ?: return@forEach
                if (canon !in skip) roots += volume
            }
        }
        os.contains("win") -> {
            File.listRoots()?.forEach { drive ->
                val path = drive.absolutePath
                if (path.startsWith("C:", ignoreCase = true)) {
                    File(drive, "Users/Public").takeIf { it.isDirectory }?.let { roots += it }
                } else if (drive.isDirectory) {
                    roots += drive
                }
            }
        }
        else -> {
            File("/media").listFiles()?.filter { it.isDirectory }?.forEach { roots += it }
            File("/mnt").listFiles()?.filter { it.isDirectory }?.forEach { roots += it }
            File("/run/media").listFiles()?.filter { it.isDirectory }?.forEach { child ->
                child.listFiles()?.filter { it.isDirectory }?.forEach { roots += it }
            }
        }
    }
    return roots.toList()
}
