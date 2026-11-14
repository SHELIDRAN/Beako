package org.nekomanga.shared.backup

import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import org.nekomanga.shared.library.LibraryData
import org.nekomanga.shared.library.Settings
import org.nekomanga.shared.library.writeAtomically
import org.nekomanga.shared.net.BeakoJson

/**
 * A Beako backup: the library and settings as gzipped JSON. Sign-ins are never included; they stay
 * in Windows Credential Manager.
 */
@Serializable
data class BeakoBackup(
    val format: String = FORMAT,
    val version: Int = 1,
    /** Milliseconds since the epoch. */
    val createdAt: Long,
    val library: LibraryData,
    val settings: Settings,
) {
    companion object {
        const val FORMAT = "beako-backup"
        const val EXTENSION = "beako.json.gz"
    }
}

/** What a restore found. */
data class RestoreResult(val titles: Int, val added: Int, val settings: Settings?)

object Backups {

    fun write(file: File, backup: BeakoBackup) {
        val json = BeakoJson.encodeToString(BeakoBackup.serializer(), backup)
        val bytes =
            java.io.ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
        writeAtomically(file, bytes.toByteArray())
    }

    /**
     * Reads a Beako backup or an Android Neko backup (`.tachibk` / `.proto.gz`), telling them apart
     * by content. Neko backups give their MangaDex titles, read chapters and categories; settings
     * aren't carried over.
     *
     * @throws IOException when the file isn't a backup Beako understands.
     */
    fun read(file: File): Pair<LibraryData, Settings?> {
        val raw = file.readBytes()
        val bytes =
            if (isGzip(raw)) GZIPInputStream(raw.inputStream()).use { it.readBytes() } else raw
        // Beako backups are JSON objects; a Neko protobuf can't start with '{' (its first byte is a
        // field tag), but try it anyway if the JSON doesn't parse.
        if (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) {
            val backup =
                try {
                    BeakoJson.decodeFromString(BeakoBackup.serializer(), bytes.decodeToString())
                } catch (e: SerializationException) {
                    null
                } catch (e: IllegalArgumentException) {
                    null
                }
            if (backup != null) {
                if (backup.format != BeakoBackup.FORMAT)
                    throw IOException("This isn’t a Beako backup.")
                return backup.library to backup.settings
            }
        }
        return NekoBackup.toLibrary(NekoBackup.decode(bytes)) to null
    }

    /**
     * Daily automatic backups in [folder], keeping the newest [keep]. Returns the file written, if
     * any.
     */
    fun automatic(
        folder: File,
        backup: () -> BeakoBackup,
        today: LocalDate = LocalDate.now(),
        keep: Int = 7,
    ): File? {
        val file = File(folder, "beako-auto-$today.${BeakoBackup.EXTENSION}")
        if (file.exists()) return null
        write(file, backup())
        folder
            .listFiles { f ->
                f.name.startsWith("beako-auto-") && f.name.endsWith(BeakoBackup.EXTENSION)
            }
            .orEmpty()
            .sortedByDescending { it.name }
            .drop(keep)
            .forEach { it.delete() }
        return file
    }

    private fun isGzip(bytes: ByteArray) =
        bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
}
