package com.forge.hypertrophy.di

import android.content.Context
import com.forge.hypertrophy.data.backup.AppBackupClient
import com.forge.hypertrophy.data.backup.AppRestarter
import com.forge.hypertrophy.data.backup.AppVersionSource
import com.forge.hypertrophy.data.backup.BackupClient
import com.forge.hypertrophy.data.backup.BackupFolders
import com.forge.hypertrophy.data.backup.BackupWriter
import com.forge.hypertrophy.data.backup.DatabaseMigrator
import com.forge.hypertrophy.data.backup.FolderBackupWriter
import com.forge.hypertrophy.data.backup.PackageAppVersion
import com.forge.hypertrophy.data.backup.PlatformSqliteUserVersion
import com.forge.hypertrophy.data.backup.PreferenceSnapshotStore
import com.forge.hypertrophy.data.backup.ProcessAppRestarter
import com.forge.hypertrophy.data.backup.RoomDatabaseMigrator
import com.forge.hypertrophy.data.backup.RoomSnapshotInstaller
import com.forge.hypertrophy.data.backup.SafBackupFolders
import com.forge.hypertrophy.data.backup.SnapshotInstaller
import com.forge.hypertrophy.data.backup.SqliteUserVersion
import com.forge.hypertrophy.data.db.DATABASE_NAME
import com.forge.hypertrophy.data.backup.DataStorePreferenceSnapshotStore
import com.forge.hypertrophy.data.repository.BackupPreferencesRepository
import com.forge.hypertrophy.data.repository.DataStoreBackupPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BackupModule {
    @Binds
    @Singleton
    abstract fun bindBackupClient(impl: AppBackupClient): BackupClient

    @Binds
    @Singleton
    abstract fun bindSnapshotStore(impl: DataStorePreferenceSnapshotStore): PreferenceSnapshotStore

    @Binds
    @Singleton
    abstract fun bindMigrator(impl: RoomDatabaseMigrator): DatabaseMigrator

    @Binds
    @Singleton
    abstract fun bindFolders(impl: SafBackupFolders): BackupFolders

    @Binds
    @Singleton
    abstract fun bindWriter(impl: FolderBackupWriter): BackupWriter

    @Binds
    @Singleton
    abstract fun bindRestarter(impl: ProcessAppRestarter): AppRestarter

    @Binds
    @Singleton
    abstract fun bindAppVersion(impl: PackageAppVersion): AppVersionSource

    @Binds
    @Singleton
    abstract fun bindUserVersion(impl: PlatformSqliteUserVersion): SqliteUserVersion

    @Binds
    @Singleton
    abstract fun bindSnapshotInstaller(impl: RoomSnapshotInstaller): SnapshotInstaller

    @Binds
    @Singleton
    abstract fun bindBackupPreferences(impl: DataStoreBackupPreferencesRepository): BackupPreferencesRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabaseFile(@ApplicationContext context: Context): File = context.getDatabasePath(DATABASE_NAME)
    }
}
