package com.forge.hypertrophy.data.backup

import com.forge.hypertrophy.data.transfer.ProgramJson
import com.forge.hypertrophy.data.transfer.ProgramJsonFormat
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream

/** Bytes copied so far against the bytes expected. */
data class BackupProgress(
    val bytesDone: Long,
    val bytesTotal: Long,
) {
    val fraction: Float
        get() = if (bytesTotal <= 0L) 1f else (bytesDone.toDouble() / bytesTotal.toDouble()).toFloat().coerceIn(0f, 1f)
}

/**
 * Streams a backup zip. Entry names are checked before any byte is written
 * to disk, and a name that escapes the destination is rejected. Media files
 * sit under [MEDIA_PREFIX] one level deep.
 */
object BackupArchive {
    const val MANIFEST = "manifest.json"
    const val DATABASE = "hypertrophy.db"
    const val PROGRAMS = "programs.json"
    const val PREFERENCES = "datastore.json"
    const val MEDIA_PREFIX = "media/"

    val entries = setOf(MANIFEST, DATABASE, PROGRAMS, PREFERENCES)

    @OptIn(ExperimentalSerializationApi::class)
    fun write(
        output: OutputStream,
        manifest: BackupManifest,
        database: File,
        programs: List<ProgramJson>,
        preferences: PreferenceSnapshot,
        media: List<File> = emptyList(),
        onProgress: (BackupProgress) -> Unit = {},
    ) {
        val total = database.length() + media.sumOf { it.length() }
        var done = 0L
        onProgress(BackupProgress(done, total))
        ZipOutputStream(output).use { zip ->
            zip.put(MANIFEST) { stream ->
                ProgramJsonFormat.encodeToStream(BackupManifest.serializer(), manifest, stream)
            }
            zip.put(DATABASE) { stream ->
                database.inputStream().use { input ->
                    input.copyStreamTo(stream) { count ->
                        done += count
                        onProgress(BackupProgress(done, total))
                    }
                }
            }
            zip.put(PROGRAMS) { stream ->
                ProgramJsonFormat.encodeToStream(ListSerializer(ProgramJson.serializer()), programs, stream)
            }
            zip.put(PREFERENCES) { stream ->
                ProgramJsonFormat.encodeToStream(PreferenceSnapshot.serializer(), preferences, stream)
            }
            media.forEach { file ->
                zip.put(MEDIA_PREFIX + file.name) { stream ->
                    file.inputStream().use { input ->
                        input.copyStreamTo(stream) { count ->
                            done += count
                            onProgress(BackupProgress(done, total))
                        }
                    }
                }
            }
        }
        onProgress(BackupProgress(total, total))
    }

    fun extract(
        input: InputStream,
        destination: File,
        onProgress: (BackupProgress) -> Unit = {},
    ) {
        destination.mkdirs()
        val written = mutableSetOf<String>()
        var done = 0L
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = checkedEntryName(entry.name)
                val target = File(destination, name)
                requireInside(destination, target, entry.name)
                if (!written.add(name)) throw InvalidBackupException("Duplicate backup entry $name")
                target.parentFile?.mkdirs()
                target.outputStream().use { output ->
                    zip.copyStreamTo(output) { count ->
                        done += count
                        onProgress(BackupProgress(done, -1L))
                    }
                }
                zip.closeEntry()
            }
        }
        val missing = entries - written
        if (missing.isNotEmpty()) throw InvalidBackupException("Backup is missing ${missing.joinToString()}")
        onProgress(BackupProgress(done, done))
    }

    /** Media files that came out of [extract], if any. */
    fun extractedMedia(directory: File): List<File> =
        File(directory, MEDIA_PREFIX.trimEnd('/')).listFiles { file -> file.isFile }?.sortedBy { it.name }
            ?: emptyList()

    @OptIn(ExperimentalSerializationApi::class)
    fun readManifest(directory: File): BackupManifest =
        File(directory, MANIFEST).inputStream().use { stream ->
            ProgramJsonFormat.decodeFromStream(BackupManifest.serializer(), stream)
        }

    @OptIn(ExperimentalSerializationApi::class)
    fun readPreferences(directory: File): PreferenceSnapshot =
        File(directory, PREFERENCES).inputStream().use { stream ->
            ProgramJsonFormat.decodeFromStream(PreferenceSnapshot.serializer(), stream)
        }

    private fun ZipOutputStream.put(name: String, write: (OutputStream) -> Unit) {
        putNextEntry(ZipEntry(name))
        write(this)
        closeEntry()
    }
}

internal fun checkedEntryName(raw: String): String {
    if (raw.isEmpty() || raw.contains('\u0000')) throw ZipSlipException(raw)
    val slash = raw.replace('\\', '/')
    if (slash.startsWith("/") || slash.contains(":")) throw ZipSlipException(raw)
    if (slash.split('/').any { it == ".." }) throw ZipSlipException(raw)
    val normalized = slash.split('/').filter { it.isNotEmpty() && it != "." }.joinToString("/")
    if (normalized in BackupArchive.entries) return normalized
    if (isMediaEntry(normalized)) return normalized
    throw InvalidBackupException("Unexpected backup entry $normalized")
}

private fun isMediaEntry(name: String): Boolean {
    if (!name.startsWith(BackupArchive.MEDIA_PREFIX)) return false
    val rest = name.removePrefix(BackupArchive.MEDIA_PREFIX)
    return rest.isNotEmpty() && !rest.contains('/')
}

private fun requireInside(destination: File, target: File, rawName: String) {
    val root = destination.canonicalFile
    val file = target.canonicalFile
    val prefix = root.path + File.separator
    if (file != root && !file.path.startsWith(prefix)) throw ZipSlipException(rawName)
}

internal fun InputStream.copyStreamTo(output: OutputStream, onChunk: (Int) -> Unit = {}) {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val count = read(buffer)
        if (count < 0) return
        output.write(buffer, 0, count)
        onChunk(count)
    }
}
