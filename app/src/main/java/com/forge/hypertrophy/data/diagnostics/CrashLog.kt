package com.forge.hypertrophy.data.diagnostics

import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Clock

private const val KEEP_LOGS = 20
const val BREADCRUMB_CAPACITY = 50

/** The last [capacity] screen changes and user actions. */
class Breadcrumbs(private val capacity: Int = BREADCRUMB_CAPACITY) {
    private val lines = ArrayDeque<String>()

    fun record(message: String) {
        synchronized(lines) {
            lines.addLast(message)
            while (lines.size > capacity) lines.removeFirst()
        }
    }

    fun snapshot(): List<String> = synchronized(lines) { lines.toList() }
}

class CrashLogStore(
    private val directory: File,
    private val clock: Clock,
    private val keep: Int = KEEP_LOGS,
) {
    fun write(
        thread: Thread,
        error: Throwable,
        versionName: String,
        deviceModel: String,
        apiLevel: Int,
        breadcrumbs: List<String>,
    ): File {
        directory.mkdirs()
        val stamp = clock.instant().toString().replace(':', '-')
        val file = File(directory, "crash-$stamp.txt")
        file.writeText(report(thread, error, versionName, deviceModel, apiLevel, breadcrumbs))
        prune()
        return file
    }

    fun writeExit(kind: String, timestamp: Long, description: String, versionName: String, apiLevel: Int): File {
        directory.mkdirs()
        val file = File(directory, "exit-$timestamp-$kind.txt")
        if (!file.exists()) {
            file.writeText(
                """
                kind=$kind
                timestamp=$timestamp
                version=$versionName
                api=$apiLevel
                description=$description
                """.trimIndent(),
            )
        }
        prune()
        return file
    }

    fun list(): List<File> = logFiles().sortedByDescending { it.lastModified() }

    fun read(file: File): String = file.readText()

    fun clear() {
        logFiles().forEach { it.delete() }
        seenFile().delete()
    }

    fun seenExitTimestamps(): Set<Long> {
        val file = seenFile()
        if (!file.isFile) return emptySet()
        return file.readLines().mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun rememberExit(timestamp: Long) {
        directory.mkdirs()
        val known = seenExitTimestamps() + timestamp
        seenFile().writeText(known.joinToString("\n"))
    }

    private fun logFiles(): List<File> {
        val files = directory.listFiles { file ->
            file.isFile && (file.name.startsWith("crash-") || file.name.startsWith("exit-"))
        } ?: return emptyList()
        return files.toList()
    }

    private fun seenFile() = File(directory, "seen-exits.txt")

    private fun prune() {
        logFiles().sortedByDescending { it.lastModified() }.drop(keep).forEach { it.delete() }
    }

    private fun report(
        thread: Thread,
        error: Throwable,
        versionName: String,
        deviceModel: String,
        apiLevel: Int,
        breadcrumbs: List<String>,
    ): String {
        val trace = StringWriter()
        error.printStackTrace(PrintWriter(trace))
        val crumbs = breadcrumbs.joinToString("\n") { "- $it" }
        return """
            version=$versionName
            device=$deviceModel
            api=$apiLevel
            time=${clock.instant()}
            thread=${thread.name}
            breadcrumbs:
            $crumbs
            stack:
            $trace
        """.trimIndent()
    }
}

/**
 * Installs the process-wide handler. The report is written first, then [prior]
 * runs unchanged.
 */
fun installCrashHandler(
    store: CrashLogStore,
    breadcrumbs: Breadcrumbs,
    versionName: String,
    deviceModel: String,
    apiLevel: Int,
    prior: Thread.UncaughtExceptionHandler?,
): Thread.UncaughtExceptionHandler {
    val handler = Thread.UncaughtExceptionHandler { thread, error ->
        runCatching {
            store.write(thread, error, versionName, deviceModel, apiLevel, breadcrumbs.snapshot())
        }
        prior?.uncaughtException(thread, error)
    }
    Thread.setDefaultUncaughtExceptionHandler(handler)
    return handler
}
