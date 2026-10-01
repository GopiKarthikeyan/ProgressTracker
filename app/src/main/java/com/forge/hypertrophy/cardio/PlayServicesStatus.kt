package com.forge.hypertrophy.cardio

import android.content.Context
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PlayServicesStatus @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlayServicesAvailable {
    override fun available(): Boolean {
        return GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }
}
