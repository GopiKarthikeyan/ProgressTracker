package com.forge.hypertrophy.data.media

import com.forge.hypertrophy.domain.model.Pose
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * App-specific media storage. Rows store a path relative to [root] so a
 * backup restored on another device still points at its files.
 */
class MediaFiles(
    private val root: File,
) {
    val directory: File get() = File(root, DIRECTORY)

    fun list(): List<File> = directory.listFiles { file -> file.isFile }?.sortedBy { it.name } ?: emptyList()

    fun newVideoFile(capturedAt: Instant): File = fresh("clip-${capturedAt.toEpochMilli()}", "mp4")

    fun newRawVideoFile(capturedAt: Instant): File = fresh("raw-${capturedAt.toEpochMilli()}", "mp4")

    fun newPhotoFile(pose: Pose, capturedAt: Instant): File =
        fresh("photo-${pose.name.lowercase(Locale.US)}-${capturedAt.toEpochMilli()}", "jpg")

    fun resolve(uri: String): File {
        val candidate = File(uri)
        return if (candidate.isAbsolute) candidate else File(root, uri)
    }

    fun relative(file: File): String = "$DIRECTORY/${file.name}"

    fun delete(uri: String): Boolean {
        val file = resolve(uri)
        return !file.exists() || file.delete()
    }

    private fun fresh(stem: String, extension: String): File {
        directory.mkdirs()
        var index = 0
        while (true) {
            val name = if (index == 0) "$stem.$extension" else "$stem-$index.$extension"
            val file = File(directory, name)
            if (!file.exists()) return file
            index += 1
        }
    }

    companion object {
        const val DIRECTORY = "media"
    }
}
