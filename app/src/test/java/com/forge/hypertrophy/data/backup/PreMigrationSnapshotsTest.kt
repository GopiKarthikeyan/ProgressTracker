package com.forge.hypertrophy.data.backup

import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class PreMigrationSnapshotsTest {
    @Test
    fun snapshotsOnlyWhenTheStoredVersionIsOlder() {
        val root = tempDir()
        val database = File(root, "hypertrophy.db")
        val snapshots = File(root, "snapshots")
        val clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC)
        val store = PreMigrationSnapshots(database, snapshots, currentVersion = 6, clock = clock)

        assertNull(store.captureIfStale())

        SQLiteDatabase.openOrCreateDatabase(database.path, null).use { it.version = 6 }
        assertNull(store.captureIfStale())
        assertTrue(store.list().isEmpty())

        SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READWRITE).use { it.version = 4 }
        val copied = store.captureIfStale()
        assertTrue(copied!!.name.startsWith("hypertrophy-v4-"))
        assertEquals(4, readSqliteUserVersion(copied))
        assertEquals(1, store.list().size)
    }

    @Test
    fun keepsTheFiveMostRecentSnapshots() {
        val root = tempDir()
        val database = File(root, "hypertrophy.db")
        SQLiteDatabase.openOrCreateDatabase(database.path, null).use { it.version = 3 }
        val clock = StepClock(Instant.parse("2026-10-01T00:00:00Z"))
        val store = PreMigrationSnapshots(
            databaseFile = database,
            snapshotsDir = File(root, "snapshots"),
            currentVersion = 6,
            clock = clock,
        )
        repeat(6) {
            clock.step()
            store.captureIfStale()
        }
        assertEquals(5, store.list().size)
    }

    @Test
    fun restoreReplacesTheLiveFileThroughTheAtomicSwap() {
        val root = tempDir()
        val live = File(root, "live.db")
        live.writeText("current")
        val snapshot = File(root, "old.db")
        snapshot.writeText("previous")
        var closed = false
        installSnapshot(live, snapshot) { closed = true }
        assertTrue(closed)
        assertEquals("previous", live.readText())
        assertTrue(snapshot.exists())
    }

    private fun tempDir(): File = File(System.getProperty("java.io.tmpdir"), "snap-" + System.nanoTime()).apply { mkdirs() }
}

private class StepClock(start: Instant) : Clock() {
    private var current = start
    fun step() {
        current = current.plusSeconds(1)
    }

    override fun instant(): Instant = current
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
}
