package org.nekomanga.shared.download

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.PageList
import org.nekomanga.shared.model.Title
import org.nekomanga.shared.net.ApiError
import org.nekomanga.shared.net.BeakoJson

/** Downloads one image's bytes. */
fun interface ImageFetcher {
    /** @throws IOException when the image can't be downloaded. */
    suspend fun bytes(url: String): ByteArray
}

enum class DownloadState {
    QUEUED,
    DOWNLOADING,
    FAILED,
}

data class DownloadJob(
    val title: Title,
    val chapter: Chapter,
    val state: DownloadState = DownloadState.QUEUED,
    val done: Int = 0,
    val total: Int = 0,
    val error: String? = null,
) {
    val key: String
        get() = "${title.source}:${title.id}/${chapter.id}"
}

/** Written last into a chapter folder; its presence means the chapter is complete. */
@Serializable private data class ChapterManifest(val chapterId: String, val pages: List<String>)

/**
 * Downloads chapters to `<root>/<title>/<chapter>/` one at a time, for offline reading. Each
 * chapter goes to a temporary folder first and is moved into place only when every page is saved,
 * so a crash never leaves a half-downloaded chapter that looks complete.
 */
class DownloadManager(
    private val root: File,
    private val scope: CoroutineScope,
    /** The image URLs of a chapter (MangaDex for now; extensions later). */
    private val pageList: suspend (Chapter) -> PageList,
    private val images: ImageFetcher,
) {
    private val state = MutableStateFlow<List<DownloadJob>>(emptyList())

    /** Chapters waiting, downloading or failed; finished ones leave the queue. */
    val queue: StateFlow<List<DownloadJob>> = state.asStateFlow()

    private val finished = MutableStateFlow(0)

    /** Goes up each time a chapter finishes or is deleted, so screens can refresh. */
    val changes: StateFlow<Int> = finished.asStateFlow()

    var paused: Boolean = false
        private set

    private var worker: Job? = null

    fun enqueue(title: Title, chapters: List<Chapter>) {
        val wanted =
            chapters
                .filter { it.externalUrl == null && !isDownloaded(title, it) }
                .map { DownloadJob(title, it) }
        state.update { queue ->
            val keys = queue.map { it.key }.toSet()
            queue + wanted.filter { it.key !in keys }
        }
        startWorker()
    }

    fun pause() {
        paused = true
        worker?.cancel()
        worker = null
        state.update { queue ->
            queue.map {
                if (it.state == DownloadState.DOWNLOADING) it.copy(state = DownloadState.QUEUED)
                else it
            }
        }
    }

    fun resume() {
        paused = false
        state.update { queue ->
            queue.map {
                if (it.state == DownloadState.FAILED)
                    it.copy(state = DownloadState.QUEUED, error = null)
                else it
            }
        }
        startWorker()
    }

    fun cancel(job: DownloadJob) {
        val wasRunning =
            state.value.firstOrNull { it.key == job.key }?.state == DownloadState.DOWNLOADING
        state.update { queue -> queue.filterNot { it.key == job.key } }
        if (wasRunning) {
            worker?.cancel()
            worker = null
            startWorker()
        }
    }

    fun clearFailed() = state.update { queue ->
        queue.filterNot { it.state == DownloadState.FAILED }
    }

    fun isDownloaded(title: Title, chapter: Chapter): Boolean =
        File(chapterDir(title, chapter), MANIFEST).exists()

    /** Local page files of a downloaded chapter, in order, or null when it isn't downloaded. */
    fun localPages(title: Title, chapter: Chapter): List<File>? {
        val dir = chapterDir(title, chapter)
        val manifest = File(dir, MANIFEST)
        if (!manifest.exists()) return null
        return runCatching {
            BeakoJson.decodeFromString(ChapterManifest.serializer(), manifest.readText())
                .pages
                .map { File(dir, it) }
                .takeIf { files -> files.all { it.exists() } }
        }
            .getOrNull()
    }

    /** Titles (by "source:id") with at least one downloaded chapter. */
    fun downloadedTitleKeys(): Set<String> =
        root.listFiles().orEmpty().mapNotNullTo(mutableSetOf()) { dir ->
            val key = File(dir, TITLE_KEY).takeIf { it.exists() }?.readText()?.trim()
            key?.takeIf { dir.listFiles().orEmpty().any { File(it, MANIFEST).exists() } }
        }

    fun delete(title: Title, chapter: Chapter) {
        chapterDir(title, chapter).deleteRecursively()
        finished.update { it + 1 }
    }

    fun deleteTitle(title: Title) {
        titleDir(title).deleteRecursively()
        finished.update { it + 1 }
    }

    private fun startWorker() {
        if (paused || worker?.isActive == true) return
        worker = scope.launch { work() }
    }

    private suspend fun work() {
        while (true) {
            val next = state.value.firstOrNull { it.state == DownloadState.QUEUED } ?: return
            set(next.key) { it.copy(state = DownloadState.DOWNLOADING, done = 0) }
            try {
                download(next)
                state.update { queue -> queue.filterNot { it.key == next.key } }
                finished.update { it + 1 }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                set(next.key) { it.copy(state = DownloadState.FAILED, error = e.message) }
            } catch (e: IOException) {
                set(next.key) {
                    it.copy(
                        state = DownloadState.FAILED,
                        error = e.message ?: "Couldn’t save the pages",
                    )
                }
            }
        }
    }

    private suspend fun download(job: DownloadJob) {
        val urls = pageList(job.chapter).urls
        if (urls.isEmpty()) throw IOException("This chapter has no pages")
        set(job.key) { it.copy(total = urls.size) }
        val target = chapterDir(job.title, job.chapter)
        val temp = File(target.parentFile, target.name + ".part")
        temp.deleteRecursively()
        temp.mkdirs()
        File(titleDir(job.title), TITLE_KEY).writeText("${job.title.source}:${job.title.id}")
        val names = mutableListOf<String>()
        for ((index, url) in urls.withIndex()) {
            val extension =
                url.substringAfterLast('.', "jpg").substringBefore('?').take(4).ifEmpty { "jpg" }
            val name = "%03d.%s".format(index + 1, extension)
            File(temp, name).writeBytes(images.bytes(url))
            names += name
            set(job.key) { it.copy(done = index + 1) }
        }
        File(temp, MANIFEST)
            .writeText(
                BeakoJson.encodeToString(
                    ChapterManifest.serializer(),
                    ChapterManifest(job.chapter.id, names),
                ),
            )
        target.deleteRecursively()
        Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    private fun set(key: String, change: (DownloadJob) -> DownloadJob) = state.update { queue ->
        queue.map { if (it.key == key) change(it) else it }
    }

    private fun titleDir(title: Title) = File(root, safeName("${title.title} [${title.id}]"))

    private fun chapterDir(title: Title, chapter: Chapter): File {
        val name =
            listOfNotNull(chapter.number?.let { "Ch $it" }, chapter.group).joinToString(" - ")
        return File(titleDir(title), safeName("${name.ifEmpty { "Chapter" }} [${chapter.id}]"))
    }

    companion object {
        private const val MANIFEST = "chapter.json"
        private const val TITLE_KEY = ".beako-title"

        /** A folder name Windows accepts: no reserved characters, no trailing dots or spaces. */
        fun safeName(name: String): String =
            name
                .replace(Regex("""[<>:"/\\|?*\u0000-\u001F]"""), "_")
                .trim()
                .trimEnd('.', ' ')
                .take(120)
                .ifEmpty { "untitled" }
    }
}
