package com.forge.hypertrophy.data.diagnostics

import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashLogTest {
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)

    @Test
    fun handlerWritesTheReportThenChainsToThePriorHandler() {
        val directory = tempDir()
        val store = CrashLogStore(directory, clock)
        val breadcrumbs = Breadcrumbs()
        breadcrumbs.record("screen:today")
        breadcrumbs.record("LogSet")
        val chained = AtomicInteger(0)
        val prior = Thread.UncaughtExceptionHandler { _, error ->
            assertEquals("boom", error.message)
            chained.incrementAndGet()
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        try {
            val handler = installCrashHandler(
                store = store,
                breadcrumbs = breadcrumbs,
                versionName = "0.1.0",
                deviceModel = "test-device",
                apiLevel = 34,
                prior = prior,
            )
            assertEquals(handler, Thread.getDefaultUncaughtExceptionHandler())
            handler.uncaughtException(Thread.currentThread(), IllegalStateException("boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(previous)
        }

        assertEquals(1, chained.get())
        val text = store.list().single().readText()
        assertTrue(text.contains("IllegalStateException"))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("version=0.1.0"))
        assertTrue(text.contains("device=test-device"))
        assertTrue(text.contains("api=34"))
        assertTrue(text.contains("screen:today"))
        assertTrue(text.contains("LogSet"))
    }

    @Test
    fun breadcrumbsKeepTheMostRecentFifty() {
        val breadcrumbs = Breadcrumbs(capacity = 50)
        repeat(60) { index -> breadcrumbs.record("n$index") }
        val snapshot = breadcrumbs.snapshot()
        assertEquals(50, snapshot.size)
        assertEquals("n10", snapshot.first())
        assertEquals("n59", snapshot.last())
    }

    @Test
    fun crashFilesKeepTheMostRecentTwenty() {
        val directory = tempDir()
        val store = CrashLogStore(directory, steppingClock(), keep = 20)
        repeat(21) { index ->
            store.write(Thread.currentThread(), IllegalStateException("n$index"), "0", "d", 30, emptyList())
        }
        assertEquals(20, store.list().size)
    }

    @Test
    fun exitRecorderKeepsAnrAndLowMemoryAndSkipsDuplicates() {
        val store = CrashLogStore(tempDir(), clock)
        val recorder = ProcessExitRecorder(store)
        val exits = listOf(
            ProcessExit(ProcessExitKind.ANR, 10L, "input dispatch"),
            ProcessExit(ProcessExitKind.LOW_MEMORY, 20L, "trim"),
            ProcessExit(ProcessExitKind.OTHER, 30L, "user"),
        )
        recorder.record(exits, "0.1.0", 34)
        recorder.record(exits, "0.1.0", 34)
        val files = store.list()
        assertEquals(2, files.size)
        val text = files.joinToString("\n") { it.readText() }
        assertTrue(text.contains("ANR"))
        assertTrue(text.contains("LOW_MEMORY"))
        assertTrue(!text.contains("user"))
    }

    private fun steppingClock(): Clock {
        var instant = Instant.parse("2026-10-01T00:00:00Z")
        return object : Clock() {
            override fun instant(): Instant {
                instant = instant.plusSeconds(1)
                return instant
            }

            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId) = this
        }
    }

    private fun tempDir(): File = File(System.getProperty("java.io.tmpdir"), "crash-" + System.nanoTime()).apply { mkdirs() }
}
