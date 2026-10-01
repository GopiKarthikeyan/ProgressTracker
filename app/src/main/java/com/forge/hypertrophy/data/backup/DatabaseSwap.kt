package com.forge.hypertrophy.data.backup

import java.io.File

/**
 * Replaces the live database file by renaming it aside and renaming the
 * incoming file into its place. Both files are in the same directory, so the
 * rename is atomic on the filesystem.
 */
internal class DatabaseSwap(private val live: File) {
    private val aside = File(live.parentFile, live.name + ".bak")
    private val staged = File(live.parentFile, live.name + ".incoming")

    fun install(incoming: File, close: () -> Unit) {
        live.parentFile?.mkdirs()
        incoming.inputStream().use { input ->
            staged.outputStream().use { output ->
                input.copyStreamTo(output)
                output.fd.sync()
            }
        }
        close()
        File(live.path + "-wal").delete()
        File(live.path + "-shm").delete()
        if (aside.exists() && !aside.delete()) error("Could not clear the previous database backup")
        if (live.exists() && !live.renameTo(aside)) {
            staged.delete()
            error("Could not move the live database aside")
        }
        if (!staged.renameTo(live)) {
            if (aside.exists()) aside.renameTo(live)
            error("Could not install the restored database")
        }
    }

    fun rollback() {
        if (!aside.exists()) return
        if (live.exists() && !live.delete()) error("Could not remove the failed database")
        if (!aside.renameTo(live)) error("Could not restore the previous database")
        File(live.path + "-wal").delete()
        File(live.path + "-shm").delete()
    }

    fun discardAside() {
        aside.delete()
    }
}
