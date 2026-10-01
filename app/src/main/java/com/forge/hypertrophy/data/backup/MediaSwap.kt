package com.forge.hypertrophy.data.backup

import java.io.File

/**
 * Replaces the live media directory with the files from a backup. The
 * incoming files are copied next to the live directory first, then the two
 * directories are renamed, so a failure leaves the old media in place.
 */
internal class MediaSwap(private val live: File) {
    private val aside = File(live.parentFile, live.name + ".bak")
    private val staged = File(live.parentFile, live.name + ".incoming")

    fun stage(files: List<File>) {
        staged.deleteRecursively()
        staged.mkdirs()
        files.forEach { source ->
            source.inputStream().use { input ->
                File(staged, source.name).outputStream().use { output ->
                    input.copyStreamTo(output)
                    output.fd.sync()
                }
            }
        }
    }

    fun install() {
        aside.deleteRecursively()
        if (live.exists() && !live.renameTo(aside)) error("Could not move the live media aside")
        if (!staged.renameTo(live)) {
            if (aside.exists()) aside.renameTo(live)
            error("Could not install the restored media")
        }
    }

    fun rollback() {
        staged.deleteRecursively()
        if (!aside.exists()) return
        live.deleteRecursively()
        if (!aside.renameTo(live)) error("Could not restore the previous media")
    }

    fun discardAside() {
        aside.deleteRecursively()
    }
}
