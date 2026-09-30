package com.forge.hypertrophy.data.db

import androidx.room.TypeConverter
import com.forge.hypertrophy.domain.model.CardioSource
import com.forge.hypertrophy.domain.model.CardioType
import com.forge.hypertrophy.domain.model.ChecklistPhase
import com.forge.hypertrophy.domain.model.EntryMethod
import com.forge.hypertrophy.domain.model.Equipment
import com.forge.hypertrophy.domain.model.MediaType
import com.forge.hypertrophy.domain.model.MetricType
import com.forge.hypertrophy.domain.model.Pose
import com.forge.hypertrophy.domain.model.ProgressionRule
import com.forge.hypertrophy.domain.model.ScheduleMode
import com.forge.hypertrophy.domain.model.SessionKind
import com.forge.hypertrophy.domain.model.SessionStatus
import com.forge.hypertrophy.domain.model.SetSide
import com.forge.hypertrophy.domain.model.SetType
import com.forge.hypertrophy.domain.model.SlotCategory
import com.forge.hypertrophy.domain.model.SlotPrescription
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json

class TrainingConverters {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    @TypeConverter
    fun fromLocalDate(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(value: Instant?): String? = value?.toString()

    @TypeConverter
    fun toInstant(value: String?): Instant? = value?.let(Instant::parse)

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun fromPrescription(value: SlotPrescription): String = json.encodeToString(value)

    @TypeConverter
    fun toPrescription(value: String): SlotPrescription = json.decodeFromString(value)

    @TypeConverter fun fromEquipment(value: Equipment): String = value.name
    @TypeConverter fun toEquipment(value: String): Equipment = Equipment.valueOf(value)

    @TypeConverter fun fromScheduleMode(value: ScheduleMode): String = value.name
    @TypeConverter fun toScheduleMode(value: String): ScheduleMode = ScheduleMode.valueOf(value)

    @TypeConverter fun fromChecklistPhase(value: ChecklistPhase): String = value.name
    @TypeConverter fun toChecklistPhase(value: String): ChecklistPhase = ChecklistPhase.valueOf(value)

    @TypeConverter fun fromSlotCategory(value: SlotCategory): String = value.name
    @TypeConverter fun toSlotCategory(value: String): SlotCategory = SlotCategory.valueOf(value)

    @TypeConverter fun fromMetricType(value: MetricType): String = value.name
    @TypeConverter fun toMetricType(value: String): MetricType = MetricType.valueOf(value)

    @TypeConverter fun fromProgressionRule(value: ProgressionRule): String = value.name
    @TypeConverter fun toProgressionRule(value: String): ProgressionRule = ProgressionRule.valueOf(value)

    @TypeConverter fun fromSessionKind(value: SessionKind): String = value.name
    @TypeConverter fun toSessionKind(value: String): SessionKind = SessionKind.valueOf(value)

    @TypeConverter fun fromSessionStatus(value: SessionStatus): String = value.name
    @TypeConverter fun toSessionStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter fun fromSetSide(value: SetSide): String = value.name
    @TypeConverter fun toSetSide(value: String): SetSide = SetSide.valueOf(value)

    @TypeConverter fun fromSetType(value: SetType): String = value.name
    @TypeConverter fun toSetType(value: String): SetType = SetType.valueOf(value)

    @TypeConverter fun fromEntryMethod(value: EntryMethod): String = value.name
    @TypeConverter fun toEntryMethod(value: String): EntryMethod = EntryMethod.valueOf(value)

    @TypeConverter fun fromCardioType(value: CardioType): String = value.name
    @TypeConverter fun toCardioType(value: String): CardioType = CardioType.valueOf(value)

    @TypeConverter fun fromCardioSource(value: CardioSource): String = value.name
    @TypeConverter fun toCardioSource(value: String): CardioSource = CardioSource.valueOf(value)

    @TypeConverter fun fromMediaType(value: MediaType): String = value.name
    @TypeConverter fun toMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter fun fromPose(value: Pose?): String? = value?.name
    @TypeConverter fun toPose(value: String?): Pose? = value?.let(Pose::valueOf)
}
