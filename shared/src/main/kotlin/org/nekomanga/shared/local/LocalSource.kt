package org.nekomanga.shared.local

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipFile
import org.nekomanga.shared.model.Chapter
import org.nekomanga.shared.model.Sources
import org.nekomanga.shared.model.Title

/**
 * Manga in a folder on this PC. Each folder in [root] is a series; inside it, each folder of images
 * or `.cbz`/`.zip` file is a chapter, named so its number can be read ("Chapter 12", "Vol. 2 Ch.
 * 13", "012"). A `cover.jpg` (or `.png`/`.webp`) in the series folder is its cover, else the first
 * page of its first chapter. Archives are unpacked once into [cacheDir].
 */
class LocalSource(private val root: () -> File, private val cacheDir: File) {

    /** Every series in the folder, by name. */
    fun titles(): List<Title> =
        root()
            .listFiles()
            .orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .sortedWith(naturalOrder)
            .map(::titleOf)

    /** The series named [id], if it's in the folder. */
    fun title(id: String): Title? = folderOf(id)?.let(::titleOf)

    /** A series' chapters, newest first like the other sources'. */
    fun chapters(title: Title): List<Chapter> {
        val folder = folderOf(title.id) ?: return emptyList()
        return chapterFiles(folder)
            .map { file ->
                val name = file.nameWithoutExtension.takeIf { file.isFile } ?: file.name
                val (number, volume) = numbers(name)
                Chapter(
                    source = Sources.LOCAL,
                    id = "${folder.name}/${file.name}",
                    number = number,
                    volume = volume,
                    title = name.takeIf { number == null },
                    language = "",
                    publishedAt = Instant.ofEpochMilli(file.lastModified()).toString(),
                    pages = 0,
                )
            }
            .sortedWith(
                compareByDescending<Chapter> { it.number?.toDoubleOrNull() ?: -1.0 }
                    .thenByDescending { it.title },
            )
    }

    /** A chapter's page images, in reading order. */
    fun pages(chapter: Chapter): List<File> {
        val file = root().resolve(chapter.id).normalize()
        // A chapter id can't point outside the local folder.
        if (!file.toPath().startsWith(root().toPath().normalize())) return emptyList()
        return when {
            file.isDirectory -> images(file)
            file.isFile && file.extension.lowercase() in ARCHIVES -> images(unpack(file))
            else -> emptyList()
        }
    }

    private fun folderOf(id: String): File? =
        root().resolve(id).takeIf { it.isDirectory && it.parentFile == root() }

    private fun titleOf(folder: File): Title {
        val cover =
            COVERS.firstNotNullOfOrNull { name -> folder.resolve(name).takeIf { it.isFile } }
                ?: chapterFiles(folder).firstOrNull()?.let { first ->
                    when {
                        first.isDirectory -> images(first).firstOrNull()
                        else -> images(unpack(first)).firstOrNull()
                    }
                }
        return Title(
            source = Sources.LOCAL,
            id = folder.name,
            title = folder.name,
            coverUrl = cover?.let { LOCAL_FILE + it.absolutePath },
        )
    }

    private fun chapterFiles(folder: File): List<File> =
        folder
            .listFiles()
            .orEmpty()
            .filter {
                (it.isDirectory && images(it).isNotEmpty()) ||
                    (it.isFile && it.extension.lowercase() in ARCHIVES)
            }
            .sortedWith(naturalOrder)

    private fun images(folder: File): List<File> =
        folder
            .listFiles()
            .orEmpty()
            .filter {
                it.isFile &&
                    it.extension.lowercase() in IMAGES &&
                    it.nameWithoutExtension != "cover"
            }
            .sortedWith(naturalOrder)

    /** Unpacks an archive's images into the cache once; a changed archive is unpacked again. */
    private fun unpack(archive: File): File {
        val key = sha("${archive.absolutePath}|${archive.length()}|${archive.lastModified()}")
        val target = cacheDir.resolve(key)
        if (target.resolve(".done").exists()) return target
        target.mkdirs()
        ZipFile(archive).use { zip ->
            var index = 0
            zip.entries()
                .asSequence()
                .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in IMAGES }
                .sortedWith(compareBy(naturalNames) { it.name })
                .forEach { entry ->
                    // Flattened, numbered names: entries can't escape the folder.
                    val ext = entry.name.substringAfterLast('.').lowercase()
                    val out = target.resolve("%04d.%s".format(index++, ext))
                    zip.getInputStream(entry).use { input ->
                        out.outputStream().use { input.copyTo(it) }
                    }
                }
        }
        target.resolve(".done").writeText("")
        return target
    }

    companion object {
        /** Marks a cover "URL" that is a file on this PC (the desktop app maps it to a file). */
        const val LOCAL_FILE = "beako-file:"

        private val IMAGES = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "bmp")
        private val ARCHIVES = setOf("cbz", "zip")
        private val COVERS = listOf("cover.jpg", "cover.jpeg", "cover.png", "cover.webp")

        private val NUMBER = Regex("""(?i)(?:ch(?:apter|\.)?\s*)(\d+(?:\.\d+)?)""")
        private val ANY_NUMBER = Regex("""(\d+(?:\.\d+)?)""")
        private val VOLUME = Regex("""(?i)\bv(?:ol(?:ume)?)?\.?\s*(\d+)""")

        /** (chapter number, volume) read from a file name; nulls when there is none. */
        fun numbers(name: String): Pair<String?, String?> {
            val volume = VOLUME.find(name)?.groupValues?.get(1)
            val rest = VOLUME.replace(name, " ")
            val number =
                (NUMBER.find(rest) ?: ANY_NUMBER.find(rest))?.groupValues?.get(1)?.let {
                    val trimmed = it.trimStart('0').ifEmpty { "0" }
                    if ('.' in trimmed) trimmed.trimEnd('0').trimEnd('.') else trimmed
                }
            return number to volume
        }

        /** "Chapter 2" before "Chapter 10": digits compare as numbers. */
        val naturalNames: Comparator<String> = Comparator { a, b ->
            val pa = parts(a)
            val pb = parts(b)
            for (i in 0 until minOf(pa.size, pb.size)) {
                val x = pa[i]
                val y = pb[i]
                val c =
                    if (x.first().isDigit() && y.first().isDigit())
                        x.toBigInteger().compareTo(y.toBigInteger())
                    else x.compareTo(y, ignoreCase = true)
                if (c != 0) return@Comparator c
            }
            pa.size - pb.size
        }

        private val naturalOrder: Comparator<File> = compareBy(naturalNames) { it.name }

        private fun parts(s: String): List<String> =
            Regex("""\d+|\D+""").findAll(s).map { it.value }.toList().ifEmpty { listOf("") }

        private fun sha(text: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(text.toByteArray())
                .joinToString("") { "%02x".format(it) }
                .take(24)
    }
}
