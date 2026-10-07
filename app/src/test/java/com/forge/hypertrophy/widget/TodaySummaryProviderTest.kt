package com.forge.hypertrophy.widget

import com.forge.hypertrophy.data.dao.DaoTest
import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.entity.RoutineDayEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.RoomProgramRepository
import com.forge.hypertrophy.data.repository.RoomRoutineRepository
import com.forge.hypertrophy.data.repository.RoomSessionRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.data.schedule.ScheduleLoader
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodaySummaryProviderTest : DaoTest() {
    // 2026-10-05 is a Monday.
    private val clock: Clock = Clock.fixed(Instant.parse("2026-10-05T12:00:00Z"), ZoneOffset.UTC)
    private val cursor = MemoryCursor()

    private fun provider(): TodaySummaryProvider {
        val sessions = RoomSessionRepository(db.sessionDao())
        val loader = ScheduleLoader(
            RoomProgramRepository(db.programDao()),
            RoomRoutineRepository(db.routineDao()),
            sessions,
            FakePreferences(),
            cursor,
        )
        return TodaySummaryProvider(loader, sessions, clock)
    }

    private suspend fun program(mode: ScheduleMode): Long {
        val id = db.programDao().insert(
            ProgramEntity(name = "p", scheduleMode = mode, rollingSequence = 0, deloadActive = false, deloadStartedOn = null),
        )
        db.programDao().setActive(id)
        return id
    }

    private suspend fun day(programId: Long, label: String, sequence: Int, weekday: Int? = null, rest: Boolean = false) {
        db.routineDao().insertDay(RoutineDayEntity(programId = programId, label = label, dayOfWeek = weekday, sequenceIndex = sequence, isRest = rest))
    }

    private suspend fun completed(date: LocalDate) {
        db.sessionDao().insert(
            WorkoutSessionEntity(
                date = date,
                dayId = null,
                kind = SessionKind.GYM,
                status = SessionStatus.COMPLETED,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = null,
                completedAt = clock.instant(),
            ),
        )
    }

    @Test
    fun noProgramGivesTheEmptySummary() = runBlocking {
        assertEquals(TodaySummary.Empty, provider().summary())
    }

    @Test
    fun fixedScheduleShowsTodaysDayAndStreak() = runBlocking {
        val programId = program(ScheduleMode.FIXED)
        day(programId, "push", 0, weekday = 1)
        day(programId, "pull", 1, weekday = 7)
        day(programId, "rest", 2, weekday = 6, rest = true)
        completed(LocalDate.of(2026, 10, 4))

        val summary = provider().summary()

        assertEquals("push", summary.dayLabel)
        assertFalse(summary.isRest)
        assertFalse(summary.completedToday)
        assertFalse(summary.inProgress)
        // Sunday completed, Saturday was a rest day that ended: a 2-day streak.
        assertEquals(2, summary.streak)
    }

    @Test
    fun rollingScheduleFollowsThePointerAndMarksTodayDone() = runBlocking {
        val programId = program(ScheduleMode.ROLLING)
        day(programId, "push", 0)
        day(programId, "rest", 1, rest = true)
        completed(LocalDate.of(2026, 10, 5))

        val summary = provider().summary()

        assertEquals("push", summary.dayLabel)
        assertTrue(summary.completedToday)
        assertEquals(1, summary.streak)
    }

    @Test
    fun scheduledRestDayIsFlagged() = runBlocking {
        val programId = program(ScheduleMode.FIXED)
        day(programId, "rest", 0, weekday = 1, rest = true)

        val summary = provider().summary()

        assertEquals("rest", summary.dayLabel)
        assertTrue(summary.isRest)
    }
}

private class MemoryCursor : ScheduleCursorRepository {
    private val swaps = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
    private val rolling = MutableStateFlow<Map<LocalDate, Long>>(emptyMap())
    private val rests = MutableStateFlow<Set<LocalDate>>(emptySet())
    override val fixedSwaps = swaps
    override val rollingDayByDate = rolling
    override val autoCompletedRests = rests

    override suspend fun save(fixedSwaps: Map<LocalDate, Long>, rollingDayByDate: Map<LocalDate, Long>, autoCompletedRests: Set<LocalDate>) {
        swaps.value = fixedSwaps
        rolling.value = rollingDayByDate
        rests.value = autoCompletedRests
    }
}

private class FakePreferences : TrainingPreferencesRepository {
    private val reconciled = MutableStateFlow<LocalDate?>(null)
    private val plates = MutableStateFlow<List<Double>>(emptyList())
    private val rest = MutableStateFlow(120)
    private val end = MutableStateFlow<Long?>(null)
    private val defaultRest = MutableStateFlow(90)

    override val lastReconciledDate = reconciled
    override val plateInventoryKg = plates
    override val transitionRestSeconds = rest
    override val activeTimerEndElapsedRealtime: Flow<Long?> = end
    override val defaultRestSeconds: Flow<Int> = defaultRest

    override suspend fun setLastReconciledDate(date: LocalDate?) {
        reconciled.value = date
    }

    override suspend fun setPlateInventoryKg(platesKg: List<Double>) {
        plates.value = platesKg
    }

    override suspend fun setTransitionRestSeconds(seconds: Int) {
        rest.value = seconds
    }

    override suspend fun setActiveTimerEndElapsedRealtime(elapsedRealtime: Long?) {
        end.value = elapsedRealtime
    }

    override suspend fun setDefaultRestSeconds(seconds: Int) {
        defaultRest.value = seconds
    }
}
