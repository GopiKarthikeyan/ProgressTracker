# Review checklists

## Phase 0

- Every version in libs.versions.toml exists and is stable; the KSP version matches the Kotlin version; the Compose compiler plugin is applied.
- A Hilt @HiltAndroidApp Application class exists and is registered in the manifest.
- Pure black also applies to the status bar, navigation bar, and edge-to-edge insets.
- The manifest declares REQUEST_IGNORE_BATTERY_OPTIMIZATIONS; POST_NOTIFICATIONS is requested only on API 33+.
- Camera, mic, and location permissions are NOT requested at startup.
- .cursor/rules/project.mdc and docs/SPEC.md exist and are complete.

## Phase 1

- Every entity and field from the Phase 1 prompt is present.
- Foreign-key onDelete choices are sensible: session → slots → sets cascade; media and routine references use SET_NULL.
- Every foreign-key column is indexed.
- Enums are stored by name, not ordinal; converters exist for LocalDate, Instant, and lists.
- exportSchema = true and the schemas/ directory is committed.
- SessionSlot really serializes the prescription snapshot; Biometrics.date is unique.
- A MigrationTestHelper harness exists in androidTest. It runs only with `./gradlew connectedDebugAndroidTest` on a connected device or emulator. `./gradlew testDebugUnitTest` does not cover migrations. There is no destructive fallback anywhere.
- DAO tests use an in-memory DB.

## Phase 2

- grep: no android.* imports in domain/; no LocalDate.now(), Instant.now(), or System.currentTimeMillis() outside the injected Clock.
- kg arithmetic is rounded to a fixed precision, with no floating-point drift (e.g. 82.49999).
- Tests cover: top-of-range bump, fixed-target bump, downward suggestion, zero-set session, stall after 3, cold start from another slot, progressionRule NONE / LINEAR, incrementOverrideKg.
- Static engine: every stage transition, form tap required, mastery → next tier, promote/demote.
- Deload: added-load-only for weighted bodyweight, rounding, freeze, restore to pre-deload weights, auto-end after one rotation.
- Streak and schedule tests in both modes, including month/year boundaries, rest-slot auto-complete, takeRestNow / skipToNext / swapWithTomorrow.
- Milestones: month-end edge (Jan 31 + 1 month), sparse library, empty library.
- Readiness threshold boundary (4 vs 5). ShortOnTimePlanner drops optional slots before isolation sets.
- At least one test suite uses the real program data.

## Phase 3

- Import runs in a single transaction and rolls back fully on any error.
- Validation rejects dangling references, duplicate weekday mappings in FIXED mode, and invalid ranges (min > max).
- A round-trip export → import test compares the data.
- A test proves editing a slot does not change existing SessionSlot snapshots.
- Reordering leaves no gaps or duplicate order indices.
- "Load sample program" exists only in debug builds.

## Phase 4

- State machine covers: supersets, transition rest, timed blocks with end-early, skip-with-reason, alternatives, mid-session reorder/swap, checklists.
- Process death: a session in progress restores from Room plus the stored end time (test it).
- The timer computes remaining time from an elapsedRealtime end target, never from per-second decrements.
- Overtime count-up to max works; the 15s warning re-arms after +30s.
- Manifest: foregroundServiceType="specialUse" with the PROPERTY_SPECIAL_USE_FGS_SUBTYPE property; startForeground is called promptly.
- The WakeLock has a timeout and is always released; AudioFocus is abandoned; TTS is initialized and shut down with the service.
- Notification PendingIntents use FLAG_IMMUTABLE; the actions work.
- Volume-key interception is active ONLY on the workout screen; 500ms debounce; undo actually deletes the SetEntry row.
- FLAG_KEEP_SCREEN_ON is cleared when leaving the screen.
- Timer ticks don't recompose the whole screen (state is split or read in lambdas).
- All touch targets ≥ 72dp; no keyboard needed to log a set.
- ReadinessAdvisor and ShortOnTimePlanner are actually wired into the UI.
- Also produce a MANUAL DEVICE TEST SCRIPT covering: locking the screen during rest, killing the app mid-rest, rotating, music playing (ducking), Bluetooth shutter remote, headphone button with and without music, undo.

## Phase 5

- PRAGMA wal_checkpoint(FULL) runs before the DB file is copied.
- Restore closes the DB, extracts to a temp location, and swaps atomically.
- Zip-slip protection: every entry path is validated before extraction.
- The manifest version is checked; newer schemas are refused; older ones are migrated.
- Files are streamed, never fully loaded into memory.
- The persisted SAF URI permission is taken for auto-backup.
- The round-trip test compares actual row data, not just "no exception."

## Phase 6

- Streak and PRs come from the domain calculators, not reimplemented in the UI.
- Aggregations happen in SQL; no N+1 queries per day or per exercise.
- Every chart and card has an empty state (fresh install).
- The 7-day moving average handles missing days correctly.
- Schedule actions call the domain use cases and refresh the Today card.

## Phase 7

- Manifest: location foreground service type; FINE and COARSE location; NO background location permission.
- The LocationManager fallback path works when Google Play Services is absent (test via the LocationSource interface).
- Accuracy and jitter filtering, auto-pause, and splits are unit-tested with a recorded track.
- TrackPoints are inserted in batches.
- The weather call has a timeout, fails silently, and is the only network code (grep Retrofit/OkHttp).
- The gear retirement alert fires at the configured limit.

## Phase 8

- CameraX is bound to the lifecycle; permissions are requested at first use; pre-roll can be cancelled.
- Transformer runs off the main thread with progress and cancellation; trims are clamped when a clip is shorter than the trim.
- Every ExoPlayer is released in DisposableEffect; synced playback stays aligned after seeking.
- Media lives in app-specific storage; deleting a MediaItem deletes its file, and vice versa.
- Backup streams media with progress; restore brings it back and re-links the MediaItem rows.

## Phase 9

- The widget updates when a session completes and after the schedule catches up.
- The Quick Settings tile starts the timer service correctly from a locked or cold state.
- The weekly review uses the domain calculators.

## Full-app audit

Run once after Phase 5, before the first real gym session.

Do a full-app audit before I start logging real training data. Do NOT modify files.

Migration validation is not part of `./gradlew testDebugUnitTest`. The MigrationTestHelper harness lives in androidTest and only runs with `./gradlew connectedDebugAndroidTest` on a connected device or emulator. Do not treat migrations as covered unless that command has been run.

Read docs/SPEC.md and the whole codebase. Focus on anything that could lose or corrupt data: migrations, backup/restore, transactions, session recovery after a crash, and the schedule catching up after days of not opening the app. Then check timer reliability with the screen locked for 10+ minutes.

Output: Blockers only (things that would lose data or break a workout mid-session), then a manual test script I can run on my phone in 20 minutes, then a fix prompt for Cursor if needed.
