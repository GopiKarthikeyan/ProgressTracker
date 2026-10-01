package com.forge.hypertrophy.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.hypertrophy.data.backup.WeeklyAutoBackup
import com.forge.hypertrophy.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AppStartupViewModel @Inject constructor(
    weeklyAutoBackup: WeeklyAutoBackup,
    media: MediaRepository,
) : ViewModel() {
    init {
        viewModelScope.launch {
            runCatching { media.reconcile() }
            runCatching { weeklyAutoBackup.runIfDue() }
        }
    }
}
