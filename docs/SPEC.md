# Hypertrophy — master spec

Offline-first hypertrophy training app. Package `com.forge.hypertrophy`. Single Android module. Phase 0 is the foundation only: toolchain, package layout, AMOLED shell, four placeholder destinations, and first-run onboarding. Later phases implement the behavior in this spec. No exercise names are hardcoded; programs, slots, and prescriptions are data.

## Foundation

- Kotlin 2.x, Compose compiler Gradle plugin, Material 3, KSP, Hilt, Room, DataStore, kotlinx-serialization, coroutines. Versions live in `gradle/libs.versions.toml` and are stable releases. minSdk 29.
- Packages:
  - `data/entity`, `data/dao`, `data/db`, `data/repository`
  - `domain/model`, `domain/engine`, `domain/usecase`
  - `service`
  - `ui/theme`, `ui/components`, `ui/screens`
  - `di`
- Domain is pure Kotlin. No `android.*` imports. Time comes only from an injected `java.time.Clock`.
- ViewModels expose one `StateFlow<UiState>` and `onEvent(Event)`.
- Every engine and use case has unit tests.
- Schema changes bump the Room version, ship a `Migration`, and ship a migration test. `fallbackToDestructiveMigration` is forbidden.
- Units are kilograms only.
- Network is allowed only in `WeatherRepository`. Failures are silent.
- Workout screens use at least 72dp touch targets. Critical actions are never swipe-only or long-press-only.

## Theme

AMOLED: background `#000000`, white text, one neon accent (`#39FF14`). Surface tint is off so blacks stay black. Every digit is rendered as a tabular monospaced numeral (`NumericText`, monospace plus `tnum`).

## Navigation

`MainActivity` hosts Navigation Compose. Destinations: Today, Routine, Dashboard, Settings. Phase 0 screens are placeholders.

## First-run onboarding

1. Request `POST_NOTIFICATIONS` (runtime permission on API 33+; skipped when the platform does not require it). A denial still continues.
2. Guide the user to exempt the app from battery optimization with `Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Also show a short note that some manufacturers add their own background-app restrictions in device settings, and those should be turned off for this app.
3. Completion is stored in DataStore and is not shown again.

Camera, microphone, and location are requested only at first use of the feature that needs them. They are not requested during onboarding and are not declared until that feature exists.

## Scheduling

A program runs in `FIXED` mode (`dayOfWeek` slots) or `ROLLING` mode (a pointer advances on completion). Rest days are slots and auto-complete when the day ends. `ReconcileScheduleUseCase` runs on app open. There are no background jobs for reconciliation. The streak is computed per mode.

Escape hatches:

- ROLLING: take rest now, skip to next.
- FIXED: swap with tomorrow.

## Program structure

Programs can express:

- supersets
- per-side reps
- set ranges
- AMRAP / Max
- either/or alternatives
- skip-with-reason (for example "did GTG")
- prep and cooldown checklists
- timed practice blocks
- evening cardio as a separate same-day item
- the same exercise in multiple slots with different prescriptions

## Progression

Progression is per slot. Each slot has a rule: `DOUBLE`, `LINEAR`, or `NONE`, plus an optional increment override.

Double progression uses equipment-aware increments:

- barbell plate math, with a per-exercise bar weight
- dumbbell step
- cable / machine stack step

Bodyweight rep slots suggest a harder variation or added load. Weighted bodyweight logs added load only. After 3 sessions with no progress, the app suggests a change. A stall is a suggestion, not an automatic mutation.

## Static skills

Each skill has a tier ladder.

- Stage 1: 12s total
- Stage 2: 15–18s total
- Stage 3: 10s+ unbroken, then the next tier

Advancing requires the target to be met and a single "form felt solid" tap, asked once per skill per session. The user can promote or demote manually.

## Deload

One full rotation, then a prompt.

- Loaded compounds: −20% (added load only for weighted bodyweight)
- Statics: −50% total hold
- Results rounded to loadable weights
- Progression frozen during the deload
- Afterwards, loads return to the pre-deload weights

## Timers

Rest ranges count down to the minimum, show a 15s warning before the minimum, then count up in an overtime color until the maximum. "As Needed" is a stopwatch. Transition rest is separate and defaults to 2 minutes. Remaining time is computed from an `elapsedRealtime` end target so wall-clock changes do not drift the timer. The workout timer runs in a foreground service with type `specialUse`.

## Hands-free triggers

On the workout screen:

- volume keys
- a Bluetooth shutter remote
- the headphone media button, only when no other app holds media focus

500ms debounce. Distinct haptics per action. 5 seconds of undo after every hands-free log.

## Workout UX

- "Log as suggested" is the one-tap primary action
- previous-session values shown faded
- ± steppers instead of a keyboard
- TTS cues
- plates per side and the warm-up ramp shown during rest
- setup notes on the exercise card
- a 3-tap readiness check
- short-on-time mode with a live ETA
- crash-proof sessions
- a closing summary

## Cardio

Manual entry and GPS. GPS points are filtered. The route is drawn without map tiles. Spoken kilometre cues. Distance feeds gear mileage.

## Data safety

Backup and restore exist before real logging begins. Programs import and export as JSON. Migrations are real (see Foundation). The release keystore is backed up outside the repo and is never committed.

## Explicitly out of scope

No Health Connect. No S-Pen. Offline except optional weather from Open-Meteo, with no API key.

## DECISIONS

- No Health Connect, no S-Pen. Offline except optional weather (Open-Meteo, no API key).
- Scheduling: FIXED (dayOfWeek) or ROLLING (pointer advances on completion) mode. Rest days are slots and auto-complete when the day ends. ReconcileScheduleUseCase runs on app open (no background jobs). The streak is computed per mode. Escape hatches: take rest now / skip to next (ROLLING), swap with tomorrow (FIXED).
- Program structure: supersets, per-side reps, set ranges, AMRAP/Max, either/or alternatives, skip-with-reason ("did GTG"), prep/cooldown checklists, timed practice blocks, evening cardio as a separate same-day item, same exercise in multiple slots with different prescriptions.
- Progression is per SLOT, with a per-slot rule (DOUBLE, LINEAR, NONE) and an optional increment override. Double progression uses equipment-aware increments (barbell plate math with per-exercise bar weight, dumbbell step, cable/machine stack step). Bodyweight rep slots suggest a harder variation or added load. Weighted bodyweight logs ADDED load. Stall after 3 sessions with no progress → suggestion.
- Static skills: tier ladder per skill. Stage 1 = 12s total, Stage 2 = 15-18s total, Stage 3 = 10s+ unbroken, then next tier. Each advance needs the target met plus a "form felt solid" tap, asked once per skill per session. Manual promote/demote.
- Deload: all loaded compounds -20% (added load only for weighted bodyweight), statics -50% total hold, rounded to loadable weights. Progression frozen during deload. Returns to pre-deload weights afterwards. Lasts one full rotation, then prompts.
- Timers: rest range counts down to min (15s warning before min), then counts up in overtime color to max. "As Needed" = stopwatch. Separate transition rest (default 2 min). Remaining time computed from an elapsedRealtime end target. Foreground service type specialUse.
- Triggers: volume keys and a Bluetooth shutter remote on the workout screen, plus the headphone media button when no other app holds media focus. 500ms debounce, distinct haptics per action, 5s undo after every hands-free log.
- Workout UX: "Log as suggested" one-tap primary action, previous-session values shown faded, ± steppers instead of a keyboard, TTS cues, plates per side and warm-up ramp shown during rest, setup notes on the exercise card, a 3-tap readiness check, short-on-time mode with live ETA, crash-proof sessions, closing summary.
- Cardio: manual entry and GPS (filtered, route drawn with no map tiles), spoken km cues. Distance feeds gear mileage.
- Data safety: backup/restore before real logging, JSON program import/export, real migrations only, backed-up release keystore.
