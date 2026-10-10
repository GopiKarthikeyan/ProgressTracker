package com.forge.hypertrophy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.backup.WeeklyAutoBackup
import com.forge.hypertrophy.data.repository.ExerciseRepository
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.data.repository.SkillRepository
import com.forge.hypertrophy.data.schedule.ScheduleReconciler
import com.forge.hypertrophy.data.transfer.LibraryCatalogImporter
import com.forge.hypertrophy.data.transfer.LibraryCatalogProvider
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Work that runs once per app open: schedule catch-up, media reconciliation,
 * the weekly auto-backup, and a one-time built-in library seed when both
 * libraries are empty. There are no background jobs for any of these.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    schedule: ScheduleReconciler,
    widget: TodayWidgetRefresher,
    weeklyAutoBackup: WeeklyAutoBackup,
    media: MediaRepository,
    exercises: ExerciseRepository,
    skills: SkillRepository,
    catalog: LibraryCatalogProvider,
    catalogImporter: LibraryCatalogImporter,
) : ViewModel() {
    init {
        viewModelScope.launch {
            val changed = runCatching { schedule.reconcile() }.getOrDefault(false)
            if (changed) runCatching { widget.refresh() }
            runCatching { media.reconcile() }
            runCatching { weeklyAutoBackup.runIfDue() }
            runCatching {
                if (exercises.all().isEmpty() && skills.all().isEmpty()) {
                    catalogImporter.import(catalog.catalog())
                }
            }
        }
    }
}
