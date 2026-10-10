package com.forge.hypertrophy.ui.screens.cardio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.cardio.CardioServiceController
import com.forge.hypertrophy.cardio.CardioTracker
import com.forge.hypertrophy.cardio.LiveTrack
import com.forge.hypertrophy.data.dao.GearMileage
import com.forge.hypertrophy.data.entity.CardioLogEntity
import com.forge.hypertrophy.data.entity.GearEntity
import com.forge.hypertrophy.data.entity.WorkoutSessionEntity
import com.forge.hypertrophy.data.repository.CardioRepository
import com.forge.hypertrophy.data.repository.GearRepository
import com.forge.hypertrophy.data.repository.SessionRepository
import com.forge.hypertrophy.domain.cardio.CardioDistanceUnit
import com.forge.hypertrophy.domain.cardio.CardioEntryInput
import com.forge.hypertrophy.domain.cardio.CardioFieldSpec
import com.forge.hypertrophy.domain.cardio.cardioFieldSpec
import com.forge.hypertrophy.domain.cardio.defaultStyleFor
import com.forge.hypertrophy.domain.cardio.validateCardioEntry
import com.forge.hypertrophy.domain.engine.GearMileage as GearLimit
import com.forge.hypertrophy.domain.model.CardioActivity
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioStyle
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class GearRow(
    val id: Long,
    val name: String,
    val usedM: Double,
    val limitM: Int,
    val retired: Boolean,
)

data class CardioLogRow(
    val id: Long,
    val source: CardioSource,
    val activity: CardioActivity,
    val style: CardioStyle,
    val customName: String,
    val distanceM: Double,
    val durationSec: Int,
    val elevationM: Double?,
    val count: Int?,
    val gearId: Long?,
    val gearName: String?,
)

enum class CardioNotice {
    SAVED,
    INVALID,
    TRACKING,
    GPS_UNSUPPORTED,
}

data class CardioUiState(
    val activity: CardioActivity = CardioActivity.RUNNING,
    val style: CardioStyle = CardioStyle.JOG,
    val customName: String = "",
    val distanceText: String = "",
    val minutes: String = "",
    val seconds: String = "",
    val elevationText: String = "",
    val countText: String = "",
    val gearId: Long? = null,
    val gear: List<GearRow> = emptyList(),
    val logs: List<CardioLogRow> = emptyList(),
    val editingId: Long? = null,
    val tracking: Boolean = false,
    val paused: Boolean = false,
    val liveDistanceM: Double = 0.0,
    val liveMovingSec: Int = 0,
    val livePoints: List<Pair<Double, Double>> = emptyList(),
    val shoeName: String = "",
    val shoeLimitKm: String = "700",
    val openSessionId: Long? = null,
    val notice: CardioNotice? = null,
) {
    val fieldSpec: CardioFieldSpec get() = cardioFieldSpec(activity)
}

sealed interface CardioEvent {
    data class ActivityChosen(val activity: CardioActivity) : CardioEvent
    data class StyleChosen(val style: CardioStyle) : CardioEvent
    data class CustomName(val text: String) : CardioEvent
    data class Distance(val text: String) : CardioEvent
    data class Minutes(val text: String) : CardioEvent
    data class Seconds(val text: String) : CardioEvent
    data class Elevation(val text: String) : CardioEvent
    data class Count(val text: String) : CardioEvent
    data class GearChosen(val gearId: Long?) : CardioEvent
    data object SaveManual : CardioEvent
    data object StartGps : CardioEvent
    data object StopGps : CardioEvent
    data class Edit(val logId: Long) : CardioEvent
    data object SaveEdit : CardioEvent
    data class ShoeName(val text: String) : CardioEvent
    data class ShoeLimit(val text: String) : CardioEvent
    data object AddShoe : CardioEvent
    data object FinishOpen : CardioEvent
    data object DismissNotice : CardioEvent
}

@HiltViewModel
class CardioViewModel @Inject constructor(
    private val sessions: SessionRepository,
    private val cardio: CardioRepository,
    private val shoes: GearRepository,
    private val tracker: CardioTracker,
    private val service: CardioServiceController,
    private val clock: Clock,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CardioUiState())
    val uiState: StateFlow<CardioUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                shoes.observeActive(),
                cardio.observeAll(),
                cardio.observeMileage(),
                tracker.live,
                sessions.observeInProgress(),
            ) { gear, logs, mileage, live, inProgress ->
                Snapshot(gear, logs, mileage, live, inProgress)
            }.collect { snapshot ->
                val names = snapshot.gear.associate { it.id to it.name }
                val used = snapshot.mileage.associate { it.gearId to it.distanceM }
                val open = snapshot.inProgress.firstOrNull {
                    it.kind == SessionKind.CARDIO && it.id != snapshot.live.sessionId
                }
                _uiState.value = _uiState.value.copy(
                    gear = snapshot.gear.map { shoe ->
                        val distance = used[shoe.id] ?: 0.0
                        GearRow(
                            id = shoe.id,
                            name = shoe.name,
                            usedM = distance,
                            limitM = GearLimit.limitMeters(shoe.mileageLimitM),
                            retired = GearLimit.retired(distance, shoe.mileageLimitM),
                        )
                    },
                    logs = snapshot.logs.map { log ->
                        CardioLogRow(
                            id = log.id,
                            source = log.source,
                            activity = log.activity,
                            style = log.type,
                            customName = log.customName,
                            distanceM = log.distanceM,
                            durationSec = log.durationSec,
                            elevationM = log.elevationM,
                            count = log.count,
                            gearId = log.gearId,
                            gearName = log.gearId?.let { names[it] },
                        )
                    },
                    tracking = snapshot.live.active,
                    paused = snapshot.live.paused,
                    liveDistanceM = snapshot.live.distanceM,
                    liveMovingSec = snapshot.live.movingSec,
                    livePoints = snapshot.live.points.map { it.latitude to it.longitude },
                    openSessionId = open?.id,
                )
            }
        }
    }

    fun onEvent(event: CardioEvent) {
        viewModelScope.launch {
            when (event) {
                is CardioEvent.ActivityChosen -> {
                    _uiState.value = _uiState.value.copy(
                        activity = event.activity,
                        style = defaultStyleFor(event.activity),
                        gearId = if (cardioFieldSpec(event.activity).showsGear) _uiState.value.gearId else null,
                    )
                }
                is CardioEvent.StyleChosen -> _uiState.value = _uiState.value.copy(style = event.style)
                is CardioEvent.CustomName -> _uiState.value = _uiState.value.copy(customName = event.text)
                is CardioEvent.Distance -> _uiState.value = _uiState.value.copy(distanceText = event.text)
                is CardioEvent.Minutes -> _uiState.value = _uiState.value.copy(minutes = event.text)
                is CardioEvent.Seconds -> _uiState.value = _uiState.value.copy(seconds = event.text)
                is CardioEvent.Elevation -> _uiState.value = _uiState.value.copy(elevationText = event.text)
                is CardioEvent.Count -> _uiState.value = _uiState.value.copy(countText = event.text)
                is CardioEvent.GearChosen -> _uiState.value = _uiState.value.copy(gearId = event.gearId)
                CardioEvent.SaveManual -> saveManual()
                CardioEvent.StartGps -> startGps()
                CardioEvent.StopGps -> service.stop()
                is CardioEvent.Edit -> openEdit(event.logId)
                CardioEvent.SaveEdit -> saveEdit()
                is CardioEvent.ShoeName -> _uiState.value = _uiState.value.copy(shoeName = event.text)
                is CardioEvent.ShoeLimit -> _uiState.value = _uiState.value.copy(shoeLimitKm = event.text)
                CardioEvent.AddShoe -> addShoe()
                CardioEvent.FinishOpen -> _uiState.value.openSessionId?.let { tracker.recover(it) }
                CardioEvent.DismissNotice -> _uiState.value = _uiState.value.copy(notice = null)
            }
        }
    }

    private suspend fun saveManual() {
        val state = _uiState.value
        val built = buildEntry(state) ?: run {
            _uiState.value = state.copy(notice = CardioNotice.INVALID)
            return
        }
        if (validateCardioEntry(built) != null) {
            _uiState.value = state.copy(notice = CardioNotice.INVALID)
            return
        }
        val sessionId = insertSession(SessionStatus.COMPLETED)
        cardio.insert(
            CardioLogEntity(
                sessionId = sessionId,
                distanceM = built.distanceM ?: 0.0,
                durationSec = built.durationSec,
                source = CardioSource.MANUAL,
                gearId = if (state.fieldSpec.showsGear) state.gearId else null,
                tempC = null,
                uvIndex = null,
                type = built.style,
                activity = built.activity,
                customName = built.customName.trim(),
                elevationM = built.elevationM,
                count = built.count,
            ),
        )
        _uiState.value = _uiState.value.copy(
            distanceText = "",
            minutes = "",
            seconds = "",
            elevationText = "",
            countText = "",
            customName = if (state.activity == CardioActivity.CUSTOM) "" else state.customName,
            notice = CardioNotice.SAVED,
        )
    }

    private suspend fun startGps() {
        val state = _uiState.value
        if (!state.fieldSpec.allowsGps) {
            _uiState.value = state.copy(notice = CardioNotice.GPS_UNSUPPORTED)
            return
        }
        if (state.tracking) {
            _uiState.value = state.copy(notice = CardioNotice.TRACKING)
            return
        }
        val started = tracker.begin(
            activity = state.activity,
            style = state.style,
            gearId = if (state.fieldSpec.showsGear) state.gearId else null,
            customName = state.customName.trim(),
        )
        if (started == null) {
            _uiState.value = _uiState.value.copy(notice = CardioNotice.TRACKING)
            return
        }
        service.start()
    }

    private fun openEdit(logId: Long) {
        val log = _uiState.value.logs.firstOrNull { it.id == logId } ?: return
        val spec = cardioFieldSpec(log.activity)
        _uiState.value = _uiState.value.copy(
            editingId = log.id,
            activity = log.activity,
            style = log.style,
            customName = log.customName,
            distanceText = when {
                !spec.showsDistance || log.distanceM <= 0.0 -> ""
                spec.distanceUnit == CardioDistanceUnit.M -> formatMetersInput(log.distanceM)
                else -> formatKmInput(log.distanceM)
            },
            minutes = (log.durationSec / 60).toString(),
            seconds = (log.durationSec % 60).toString(),
            elevationText = log.elevationM?.let(::formatMetersInput).orEmpty(),
            countText = log.count?.toString().orEmpty(),
            gearId = log.gearId,
        )
    }

    private suspend fun saveEdit() {
        val state = _uiState.value
        val logId = state.editingId ?: return
        val built = buildEntry(state) ?: run {
            _uiState.value = state.copy(notice = CardioNotice.INVALID)
            return
        }
        if (validateCardioEntry(built) != null) {
            _uiState.value = state.copy(notice = CardioNotice.INVALID)
            return
        }
        val existing = cardio.observeAll().first().firstOrNull { it.id == logId } ?: return
        cardio.update(
            existing.copy(
                distanceM = built.distanceM ?: 0.0,
                durationSec = built.durationSec,
                type = built.style,
                activity = built.activity,
                customName = built.customName.trim(),
                elevationM = built.elevationM,
                count = built.count,
                gearId = if (state.fieldSpec.showsGear) state.gearId else null,
            ),
        )
        _uiState.value = _uiState.value.copy(editingId = null, notice = CardioNotice.SAVED)
    }

    private suspend fun addShoe() {
        val state = _uiState.value
        val name = state.shoeName.trim()
        if (name.isEmpty()) {
            _uiState.value = state.copy(notice = CardioNotice.INVALID)
            return
        }
        val limitKm = state.shoeLimitKm.trim().toDoubleOrNull()
        if (limitKm == null || limitKm <= 0.0) {
            _uiState.value = state.copy(notice = CardioNotice.INVALID)
            return
        }
        shoes.insert(
            GearEntity(
                name = name,
                mileageLimitM = (limitKm * 1_000.0).toInt(),
                archivedAt = null,
            ),
        )
        _uiState.value = _uiState.value.copy(shoeName = "", shoeLimitKm = "700", notice = CardioNotice.SAVED)
    }

    private fun buildEntry(state: CardioUiState): CardioEntryInput? {
        val duration = durationSeconds(state.minutes, state.seconds) ?: return null
        val spec = state.fieldSpec
        val distanceM = when {
            !spec.showsDistance -> null
            state.distanceText.isBlank() -> if (spec.distanceRequired) return null else null
            spec.distanceUnit == CardioDistanceUnit.M -> metersInput(state.distanceText) ?: return null
            else -> kilometresToMeters(state.distanceText) ?: return null
        }
        val elevation = if (!spec.showsElevation || state.elevationText.isBlank()) {
            null
        } else {
            metersInput(state.elevationText) ?: return null
        }
        val count = if (!spec.showsCount || state.countText.isBlank()) {
            null
        } else {
            state.countText.trim().toIntOrNull()?.takeIf { it >= 0 } ?: return null
        }
        return CardioEntryInput(
            activity = state.activity,
            style = state.style,
            customName = state.customName,
            distanceM = distanceM,
            durationSec = duration,
            elevationM = elevation,
            count = count,
        )
    }

    private suspend fun insertSession(status: SessionStatus): Long {
        return sessions.insert(
            WorkoutSessionEntity(
                date = clock.instant().atZone(clock.zone).toLocalDate(),
                dayId = null,
                kind = SessionKind.CARDIO,
                status = status,
                isDeload = false,
                isShortOnTime = false,
                readinessSleep = null,
                readinessSoreness = null,
                readinessEnergy = null,
                startedAt = if (status == SessionStatus.COMPLETED) clock.instant() else null,
                completedAt = if (status == SessionStatus.COMPLETED) clock.instant() else null,
            ),
        )
    }

    private data class Snapshot(
        val gear: List<GearEntity>,
        val logs: List<CardioLogEntity>,
        val mileage: List<GearMileage>,
        val live: LiveTrack,
        val inProgress: List<WorkoutSessionEntity>,
    )
}

internal fun kilometresToMeters(text: String): Double? {
    val kilometres = text.trim().toDoubleOrNull() ?: return null
    if (kilometres <= 0.0) return null
    return kilometres * 1_000.0
}

internal fun metersInput(text: String): Double? {
    val meters = text.trim().toDoubleOrNull() ?: return null
    if (meters <= 0.0) return null
    return meters
}

internal fun durationSeconds(minutes: String, seconds: String): Int? {
    val minuteValue = minutes.trim().toIntOrNull() ?: return null
    val secondValue = seconds.trim().ifEmpty { "0" }.toIntOrNull() ?: return null
    if (minuteValue < 0 || secondValue !in 0..59) return null
    val total = minuteValue * 60 + secondValue
    if (total <= 0) return null
    return total
}

internal fun formatKmInput(meters: Double): String {
    val kilometres = meters / 1_000.0
    return if (kilometres % 1.0 == 0.0) kilometres.toLong().toString() else kilometres.toString()
}

internal fun formatMetersInput(meters: Double): String =
    if (meters % 1.0 == 0.0) meters.toLong().toString() else meters.toString()
