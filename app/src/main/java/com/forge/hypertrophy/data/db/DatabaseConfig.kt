package com.forge.hypertrophy.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

const val DATABASE_NAME = "hypertrophy.db"
const val SCHEMA_VERSION = 6

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE biometrics ADD COLUMN bodyFatPercent REAL")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE cardio_log ADD COLUMN type TEXT NOT NULL DEFAULT 'JOG'")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE media_item ADD COLUMN capturedAt TEXT")
        db.execSQL("ALTER TABLE media_item ADD COLUMN label TEXT")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `skill_stage_event` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `skillId` INTEGER NOT NULL,
                `date` TEXT NOT NULL,
                `fromTier` INTEGER NOT NULL,
                `fromStage` INTEGER NOT NULL,
                `toTier` INTEGER NOT NULL,
                `toStage` INTEGER NOT NULL,
                `recordedAt` TEXT NOT NULL,
                FOREIGN KEY(`skillId`) REFERENCES `skill`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_skill_stage_event_skillId` ON `skill_stage_event` (`skillId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_skill_stage_event_date` ON `skill_stage_event` (`date`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE workout_session ADD COLUMN editedAt TEXT")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `slot_baseline` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `slotId` INTEGER NOT NULL,
                `weightKg` REAL,
                `repsHint` INTEGER,
                `setAt` TEXT NOT NULL,
                FOREIGN KEY(`slotId`) REFERENCES `routine_slot`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_slot_baseline_slotId` ON `slot_baseline` (`slotId`)")
    }
}

/** Real migrations only. An empty list never falls back to a destructive rebuild. */
object DatabaseMigrations {
    val ALL: Array<Migration> = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
    )
}
