package com.forge.hypertrophy.data.schedule

import com.forge.hypertrophy.data.entity.ProgramEntity
import com.forge.hypertrophy.data.repository.ProgramRepository
import com.forge.hypertrophy.data.repository.RoutineRepository
import com.forge.hypertrophy.data.repository.ScheduleCursorRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.model.ScheduleSnapshot
import com.forge.hypertrophy.domain.repository.TrainingPreferencesRepository
import com.forge.hypertrophy.domain.usecase.ReconcileScheduleUseCase
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs [ReconcileScheduleUseCase] against the stored schedule and writes the
 * result back. Called on app open and before the dashboard reads the
 * schedule, so Today and the widget never show a stale rotation. One
 * instance for the process; concurrent callers wait for each other.
 */
@Singleton
class ScheduleReconciler @Inject constructor(
    private val programs: ProgramRepository,
    private val routines: RoutineRepository,
    private val sessions: SessionRepository,
    private val preferences: TrainingPreferencesRepository,
    private val cursor: ScheduleCursorRepository,
    private val clock: Clock,
) {
    private val loader = ScheduleLoader(programs, routines, sessions, preferences, cursor)
    private val reconcile = ReconcileScheduleUseCase(clock)
    private val gate = Mutex()

    /** Returns true when something was written. */
    suspend fun reconcile(program: ProgramEntity? = null): Boolean = gate.withLock {
        val active = program ?: programs.observeActive().first() ?: return false
        val loaded = loader.load(active) ?: return false
        val reconciled = reconcile.reconcile(loaded.snapshot)
        if (reconciled == loaded.snapshot) return false
        persist(active, reconciled)
        true
    }

    private suspend fun persist(program: ProgramEntity, snapshot: ScheduleSnapshot) {
        if (program.rollingSequence != snapshot.rollingIndex) {
            programs.update(program.copy(rollingSequence = snapshot.rollingIndex))
        }
        cursor.save(
            fixedSwaps = snapshot.fixedSwaps,
            rollingDayByDate = snapshot.rollingDayByDate,
            autoCompletedRests = snapshot.autoCompletedRests,
        )
        preferences.setLastReconciledDate(snapshot.lastReconciled)
    }
}
