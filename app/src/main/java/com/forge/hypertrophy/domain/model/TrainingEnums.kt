package com.forge.hypertrophy.domain.model

enum class Equipment {
    BARBELL,
    EZ_BAR,
    DUMBBELL,
    CABLE,
    MACHINE,
    BODYWEIGHT,
    WEIGHTED_BODYWEIGHT,
    BAND,
}

enum class ScheduleMode {
    FIXED,
    ROLLING,
}

enum class ChecklistPhase {
    PREP,
    COOLDOWN,
}

enum class SlotCategory {
    PREP,
    SKILL,
    COMPOUND,
    ISOLATION,
    CORE,
    CARDIO,
    COOLDOWN,
}

enum class MetricType {
    WEIGHT_REPS,
    REPS,
    HOLD,
    HOLD_OR_REPS,
    TIMED_BLOCK,
}

enum class ProgressionRule {
    DOUBLE,
    LINEAR,
    NONE,
}

enum class SessionKind {
    GYM,
    CARDIO,
    ACTIVE_RECOVERY,
    REST,
}

enum class SessionStatus {
    PLANNED,
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
}

enum class SetSide {
    LEFT,
    RIGHT,
    BOTH,
}

enum class SetType {
    WARMUP,
    WORKING,
    AMRAP,
    DELOAD,
}

enum class EntryMethod {
    SCREEN,
    HARDWARE_KEY,
}

enum class CardioActivity {
    RUNNING,
    CYCLING,
    SWIMMING,
    ROWING,
    ELLIPTICAL,
    JUMP_ROPE,
    HIKING,
    STAIR_CLIMBER,
    SKI_ERG,
    CUSTOM,
}

/** Running style stored in the `type` column. [NONE] for non-running activities. */
enum class CardioStyle {
    JOG,
    WALK,
    INTERVALS,
    SPRINT,
    LONG_RUN,
    NONE,
}

enum class CardioSource {
    MANUAL,
    GPS,
}

enum class MediaType {
    PHOTO,
    VIDEO,
}

enum class Pose {
    FRONT,
    SIDE,
    BACK,
    QUADRICEPS,
    HAMSTRINGS,
    CALVES,
}
