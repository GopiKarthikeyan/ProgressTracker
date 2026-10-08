package com.forge.hypertrophy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.backup.WeeklyAutoBackup
import com.forge.hypertrophy.data.repository.MediaRepository
import com.forge.hypertrophy.data.schedule.ScheduleReconciler
import com.forge.hypertrophy.widget.TodayWidgetRefresher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Work that runs once per app open: schedule catch-up, media reconciliation,
 * and the weekly auto-backup. There are no background jobs for any of these.
 */
@HiltViewModel
class AppStartupViewModel @Inject constructor(
    schedule: ScheduleReconciler,
    widget: TodayWidgetRefresher,
    weeklyAutoBackup: WeeklyAutoBackup,
    media: MediaRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            val changed = runCatching { schedule.reconcile() }.getOrDefault(false)
            if (changed) runCatching { widget.refresh() }
            runCatching { media.reconcile() }
            runCatching { weeklyAutoBackup.runIfDue() }
        }
    }
}
